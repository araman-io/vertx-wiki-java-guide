package io.vertx.guides.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.guides.wiki.database.DbVerticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Promise<Void> startPromise) {
    vertx
      .deployVerticle(DbVerticle.class.getName())
      .compose(db -> {
        LOGGER.info("now deploying http verticle instances started");
        return vertx.deployVerticle(HttpVerticle.class.getName(), new DeploymentOptions().setInstances(2));
      })
      .onSuccess(result -> {
        LOGGER.info("db and 2 httpserver instances started");
        startPromise.complete();
      }).onFailure(error -> {
        LOGGER.error("something went wrong {}", error);
        startPromise.fail(error);
      });
  }

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(MainVerticle.class.getName());
  }


}
