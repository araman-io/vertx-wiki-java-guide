package io.vertx.guides.wiki.database;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.Iterator;

public class WikiDbServiceImpl implements WikiDbService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DbVerticle.class);
  private JDBCPool pool;
  private static final String SQL_CREATE_PAGES_TABLE = "create table if not exists Pages (Id integer identity primary key, Name varchar(255) unique, Content clob)";
  private static final String SQL_GET_PAGE = "select Id, Content from Pages where Name = ?";
  private static final String SQL_CREATE_PAGE = "insert into Pages values (NULL, ?, ?)";
  private static final String SQL_SAVE_PAGE = "update Pages set Content = ? where Id = ?";
  private static final String SQL_ALL_PAGES = "select Name from Pages";
  private static final String SQL_DELETE_PAGE = "delete from Pages where Id = ?";
  private static final String EMPTY_PAGE_MARKDOWN = "# A new page\n" + "\n" + "Feel-free to write in Markdown!\n";
  private static final String GET_ALL_DATA = "select Name, Content from Pages";

  public WikiDbServiceImpl(JDBCPool pool) {
    this.pool = pool;
  }

  @Override
  public Future<JsonObject> initDatabase() {
    return pool.query(SQL_CREATE_PAGES_TABLE)
      .execute()
      .compose(rows -> {
        return Future.succeededFuture(new JsonObject());
      });
  }

  @Override
  public Future<JsonObject> fetchAllPages() {
    return this.pool
      .query(SQL_ALL_PAGES)
      .execute()
      .compose(rows -> {
        JsonArray pages = new JsonArray();
        for (Row r : rows) {
          pages.add(r.getString("NAME"));
        }
        LOGGER.info("fetched following pages {} {}", pages.size());
        return Future.succeededFuture(new JsonObject().put("pages", pages));
      });
  }

  @Override
  public Future<JsonObject> renderPage(JsonObject message) {
    String pageName = message.getString("page");
    return this.pool.preparedQuery(SQL_GET_PAGE)
      .execute(Tuple.of(pageName))
      .compose(rows -> {
        JsonObject templateData = new JsonObject();
        RowIterator<Row> iterator = rows.iterator();
        if (iterator.hasNext()) {
          Row row = iterator.next();
          templateData.put("id", row.getInteger("ID"));
          templateData.put("rawContent", row.getString("CONTENT"));
          templateData.put("newPage", "no");
        } else {
          templateData.put("id", -1);
          templateData.put("rawContent", EMPTY_PAGE_MARKDOWN);
          templateData.put("newPage", "yes");
        }
        templateData.put("title", pageName);
        templateData.put("timestamp", new Date().toString());
        return Future.succeededFuture(templateData);
      });
  }

  public Future<JsonObject> deletePage(JsonObject message) {
    Integer id = message.getInteger("id");
    return this.pool
      .preparedQuery(SQL_DELETE_PAGE)
      .execute(Tuple.of(id))
      .compose(rows -> {
        return Future.succeededFuture(new JsonObject());
      });
  }

  public Future<JsonObject> upsertPage(JsonObject request) {
    Boolean newPage = request.getBoolean("newPage");
    Integer id = request.getInteger("id");
    String pageName = request.getString("title");
    String rawContent = request.getString("markDown");
    Future<RowSet<Row>> query;

    LOGGER.info("retrieved data to upsert {} {} {}", newPage, id, pageName);

    if (newPage) {
      query = this.pool.preparedQuery(SQL_CREATE_PAGE)
        .execute(Tuple.of(pageName, rawContent));
    } else {
      query = this.pool.preparedQuery(SQL_SAVE_PAGE)
        .execute(Tuple.of(rawContent, id));
    }

    return query
      .compose(o -> {
        return Future.succeededFuture(new JsonObject());
      });
  }

  public Future<JsonObject> getAllPageData() {
    return this.pool.query(GET_ALL_DATA)
      .execute()
      .compose(rows -> {
        JsonArray pages = new JsonArray();
        for (Row r : rows) {
          JsonObject page = new JsonObject()
            .put("name", r.getString("NAME"))
            .put("content", r.getString("CONTENT"));
          pages.add(page);
        }
        return Future.succeededFuture(new JsonObject().put("files", pages));
      });
  }


  public enum ErrorCodes {
    NO_ACTION_SPECIFIED,
    BAD_ACTION,
    DB_ERROR
  }
}
