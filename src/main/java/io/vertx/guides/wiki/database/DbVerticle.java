package io.vertx.guides.wiki.database;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.serviceproxy.ServiceBinder;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;

public class DbVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(DbVerticle.class);
  private JDBCPool pool;
  private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
  private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
  private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
  private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
  private static final String SQL_ALL_PAGES = "select Name from Pages";
  private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";
  private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n" + "\n" + "Feel-free to write in Markdown!\n";


  @Override
  public void start(Promise<Void> startPromise) throws Exception {
    this.pool = JDBCPool.pool(vertx, new JsonObject()
      .put("url", "jdbc:hsqldb:file:db/wiki")
      .put("driver_class", "org.hsqldb.jdbcDriver").put("max_pool_size", 30));

    WikiDbServiceImpl serviceImpl = new WikiDbServiceImpl(this.pool);
    serviceImpl
      .initDatabase()
      .onSuccess(result -> {
        LOGGER.info("database has been initialized, setting up service binders");
        ServiceBinder binder = new ServiceBinder(vertx);
        binder
          .setAddress("wikidb.queue")
          .register(WikiDbService.class, serviceImpl);
        startPromise.complete();
      })
      .onFailure(error -> {
        LOGGER.info("something went wrong with the db step  {}", error);
        startPromise.fail(error);
      });
  }

  public enum ErrorCodes {
    NO_ACTION_SPECIFIED,
    BAD_ACTION,
    DB_ERROR
  }

}
