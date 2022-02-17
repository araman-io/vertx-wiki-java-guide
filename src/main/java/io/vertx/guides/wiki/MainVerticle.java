package io.vertx.guides.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;

public class MainVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Promise<Void> startPromise) {
    vertx
      .deployVerticle(DbVerticle.class.getName())
      .compose(db -> {
        return vertx.deployVerticle(HttpVerticle.class.getName());
      })
      .onSuccess(result -> {
        LOGGER.info("db and httpserver started");
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
