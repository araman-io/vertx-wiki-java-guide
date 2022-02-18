package io.vertx.guides.wiki.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.serviceproxy.ServiceBinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(DbVerticle.class);
  private JDBCPool pool;

  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    this.pool = JDBCPool.pool(vertx, new JsonObject()
      .put("url", "jdbc:hsqldb:file:db/wiki")
      .put("driver_class", "org.hsqldb.jdbcDriver").put("max_pool_size", 30));

    WikiDbService wikiDbService = WikiDbService.create(this.pool);
    wikiDbService
      .initDatabase()
      .onSuccess(result -> {
        LOGGER.info("database has been initialized, setting up service binders");
        ServiceBinder binder = new ServiceBinder(vertx);
        binder
          .setAddress("wikidb.queue")
          .register(WikiDbService.class, wikiDbService);
        startPromise.complete();
      })
      .onFailure(error -> {
        LOGGER.info("something went wrong with the db step  {}", error);
        startPromise.fail(error);
      });
  }
}
