package com.example;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.OverflowStrategy;
import akka.stream.alpakka.cassandra.javadsl.CassandraFlow;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Session;

import java.util.function.BiFunction;

public class CassandraFlowExample {
  public static class UserComment {
    int    userId;
    String comment;

    UserComment(int userId, String comment) {
      this.userId = userId;
      this.comment = comment;
    }
  }

  public static void main(String args[]) {
    final Session session = Cluster.builder()
      .addContactPoint("127.0.0.1").withPort(9042)
      .build().connect();

    final ActorSystem system = ActorSystem.create();
    final Materializer materializer = ActorMaterializer.create(system);

    final PreparedStatement insertTemplate = session.prepare(
      "INSERT INTO akka_stream_java_test.user_comments (user_id, comment) VALUES (? ?)"
    );

    // A function to create a BoundStatement, from:
    //  - UserComment, input data
    //  - PreparedStatement, template to generate BoundStatement by supplying UserComment
    BiFunction<UserComment, PreparedStatement, BoundStatement> statementBinder =
      (userData, preparedStatement) -> preparedStatement.bind(userData.userId, userData.comment);

    final Flow<UserComment, UserComment, NotUsed> flow =
      CassandraFlow.createWithPassThrough(2, insertTemplate, statementBinder, session, system.dispatcher());

    final Source<UserComment, ActorRef> source = Source.actorRef(4, OverflowStrategy.backpressure());

    final ActorRef actorRef = source
        .via(flow)
        .to(Sink.ignore())
        .run(materializer);

    for(i=1; i<=1000; i++){
      actorRef.tell(new UserComment(1, ""), ActorRef.noSender());
    }

    // Sleep for 10 seconds, so that the stream finishes running
    Thread.sleep(10000);
  }
}
