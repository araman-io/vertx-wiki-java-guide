package io.vertx.guides.wiki;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DbVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(DbVerticle.class);
  private JDBCPool pool;
  private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
  private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
  private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
  private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
  private static final String SQL_ALL_PAGES = "select Name from Pages";
  private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";


  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    this.pool = JDBCPool.pool(vertx, new JsonObject()
      .put("url", "jdbc:hsqldb:file:db/wiki")
      .put("driver_class", "org.hsqldb.jdbcDriver").put("max_pool_size", 30));

    pool.query(SQL_CREATE_PAGES_TABLE)
      .execute()
      .onSuccess(result -> {
        vertx.eventBus().consumer("wikidb.queue", this::onMessage);  // <3>
        startPromise.complete();
        LOGGER.info("database has been initialized");
      })
      .onFailure(error -> {
        LOGGER.info("something went wrong with the db step  {}", error);
        startPromise.fail(error);
      });
  }

  private void onMessage(Message<JsonObject> message) {
    if (!message.headers().contains("action")) {
      LOGGER.error("No action header specified for message with headers {} and body {}", message.headers(), message.body().encodePrettily());
      message.fail(ErrorCodes.NO_ACTION_SPECIFIED.ordinal(), "no action specified");
    }
    String action = message.headers().get("action");
    switch (action) {
      case "all-pages":
        this.fetchAllPages(message);
      default:
        this.fetchAllPages(message);
    }
  }

  private void fetchAllPages(Message<JsonObject> message) {
    this.pool
      .query(SQL_ALL_PAGES)
      .execute()
      .onSuccess(rows -> {
        JsonArray pages = new JsonArray();
        for (Row r : rows) {
          pages.add(r.getString("NAME"));
        }
        message.reply(new JsonObject().put("pages", pages));
      })
      .onFailure(error -> {
        message.fail(ErrorCodes.DB_ERROR.ordinal(), error.getMessage());
      });
  }

  public enum ErrorCodes {
    NO_ACTION_SPECIFIED,
    BAD_ACTION,
    DB_ERROR
  }

}
