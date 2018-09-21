package me.murks.feedwatcher.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import me.murks.feedwatcher.Lookup
import me.murks.feedwatcher.model.*
import java.net.URL
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * @author zouroboros
 */
class DataStore(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    val writeDb = writableDatabase

    val readDb = readableDatabase

    init {
        writeDb.setForeignKeyConstraintsEnabled(true)
    }


    override fun onUpgrade(p0: SQLiteDatabase?, p1: Int, p2: Int) {
    }

    override fun onCreate(db: SQLiteDatabase) {
        val feedsTable = "create table $FEEDS_TABLE ($ID integer primary key, " +
                "$FEED_URL text not null, $FEED_LAST_UPDATED text)"
        val queryTable = "create table $QUERIES_TABLE ($ID integer primary key, $QUERY_NAME text)"
        val filterTable = "create table $FILTER_TABLE ($ID integer primary key, $FILTER_TYPE text, " +
                "$FILTER_QUERY_ID integer, $FILTER_INDEX integer, " +
                "foreign key ($FILTER_QUERY_ID) references $QUERIES_TABLE($ID))"
        val filterParameterTable = "create table $FILTER_PARAMETER_TABLE ($ID integer primary key, " +
                "$FILTER_PARAMETER_NAME text, $FILTER_PARAMETER_STRING_VALUE text null, " +
                "$FILTER_PARAMETER_FILTER_ID integer, " +
                "foreign key ($FILTER_PARAMETER_FILTER_ID) references $FILTER_TABLE($ID))"
        val resultTable = "create table $RESULTS_TABLE ($ID integer primary key, $RESULT_FEED_ID integer, " +
                    "$RESULT_QUERY_ID integer, $RESULT_FEED_ITEM_DATE integer, " +
                    "$RESULT_FEED_ITEM_DESCRIPTION text, $RESULT_FEED_ITEM_LINK text, " +
                    "$RESULT_FEED_ITEM_TITLE text, $RESULT_FEED_NAME text, $RESULT_FOUND integer, " +
                    "foreign key ($RESULT_FEED_ID) references $FEEDS_TABLE($ID), " +
                    "foreign key ($RESULT_QUERY_ID) references $QUERIES_TABLE($ID))"
        db.beginTransaction()
        db.execSQL(feedsTable)
        db.execSQL(queryTable)
        db.execSQL(filterTable)
        db.execSQL(filterParameterTable)
        db.execSQL(resultTable)
        db.setTransactionSuccessful()
        db.endTransaction()
    }


    fun getFeeds() : List<Feed> {
        val cursor = readDb.query(FEEDS_TABLE, null, null, null,
                null, null, null)
        val feeds = LinkedList<Feed>()
        while(cursor.moveToNext()) {
            feeds.add(feed(cursor))
        }
        return feeds
    }

    private fun feed(cursor: Cursor): Feed {
        val url = URL(cursor.getString(cursor.getColumnIndex(FEED_URL)))
        val lastUpdated = Date(cursor.getLong(cursor.getColumnIndex(FEED_LAST_UPDATED)))
        return Feed(url, lastUpdated)
    }

    fun getQueries() : List<Query> {
        val filterId = "filterId"
        val queryId = "queryId"
        val cursor = readDb.rawQuery("select $QUERIES_TABLE.$ID as $queryId, " +
                    "$FILTER_TABLE.$ID as $filterId, $FILTER_PARAMETER_TABLE.$ID as parameterId, " +
                    "* from $QUERIES_TABLE " +
                "join $FILTER_TABLE on $QUERIES_TABLE.$ID = $FILTER_TABLE.$FILTER_QUERY_ID " +
                "join $FILTER_PARAMETER_TABLE on $FILTER_TABLE.$ID " +
                    "= $FILTER_PARAMETER_TABLE.$FILTER_PARAMETER_FILTER_ID",null)

        return loadQueries(cursor, filterId, queryId)
    }

    private fun loadQueries(cursor: Cursor, filterId: String, queryId: String): List<Query> {
        val filterParameter = Lookup(HashMap<Int, MutableList<FilterParameter>>())

        while(cursor.moveToNext()) {
            filterParameter.append(cursor.getInt(cursor.getColumnIndex(FILTER_PARAMETER_FILTER_ID)),
                    filterParameter(cursor))
        }

        val filter = Lookup(HashMap<Long, MutableList<Filter>>())

        if(cursor.moveToFirst()) {
            do {
                val queryId = cursor.getLong(cursor.getColumnIndex(FILTER_QUERY_ID))
                val id = cursor.getInt(cursor.getColumnIndex(filterId))
                filter.append(queryId, filter(cursor, filterParameter, id))
            } while(cursor.moveToNext())
        }

        val queries = LinkedList<Query>()
        val loaded = HashSet<Long>()

        if(cursor.moveToFirst()) {
            do {
                val id = cursor.getLong(cursor.getColumnIndex(queryId))
                val query = query(cursor, filter, id)
                if(!loaded.contains(query.id)) {
                    queries.add(query)
                }
            } while (cursor.moveToNext())
        }

        return queries
    }

    private fun query(cursor: Cursor, filter: Lookup<Long, Filter>, id: Long): Query {
        val name = cursor.getString(cursor.getColumnIndex(QUERY_NAME))
        return Query(id, name, filter.values(id)!!)
    }

    private fun filterParameter(cursor: Cursor): FilterParameter {
        val name = cursor.getString(cursor.getColumnIndex(FILTER_PARAMETER_NAME))
        val stringValue = cursor.getString(cursor.getColumnIndex(FILTER_PARAMETER_STRING_VALUE))
        return FilterParameter(name, stringValue)
    }

    private fun filter(cursor: Cursor, parameter: Lookup<Int, FilterParameter>, id: Int): Filter {
        val type = FilterType.valueOf(cursor.getString(cursor.getColumnIndex(FILTER_TYPE)))
        val index = cursor.getInt(cursor.getColumnIndex(FILTER_INDEX))
        return Filter(type, parameter.values(id)!!, index)
    }

    fun updateQuery(query: Query): Query {
        writeDb.beginTransaction()
        deleteQuery(query)
        val query = addQuery(query)
        writeDb.setTransactionSuccessful()
        writeDb.endTransaction()
        return query
    }

    private fun deleteQuery(query: Query) {
        writeDb.beginTransaction()
        writeDb.delete(FILTER_PARAMETER_TABLE,"$FILTER_PARAMETER_FILTER_ID in " +
                "(select $ID from $FILTER_TABLE where $FILTER_QUERY_ID = ${query.id})",
                null)
        writeDb.delete(FILTER_TABLE, "$FILTER_QUERY_ID = ${query.id}", null)
        writeDb.delete(QUERIES_TABLE, "$ID = ${query.id}", null)
        writeDb.setTransactionSuccessful()
        writeDb.endTransaction()
    }

    fun addQuery(query: Query): Query {
        writeDb.beginTransaction()
        val queryId = writeDb.insert(QUERIES_TABLE, null, queryValues(query))
        val newQuery = Query(queryId, query.name, query.filter)

        for (filter in newQuery.filter) {
            val filterId = writeDb.insert(FILTER_TABLE, null, filterValues(filter, newQuery))
            for (parameter in filter.parameter) {
                writeDb.insert(FILTER_PARAMETER_TABLE, null,
                        parameterValues(parameter, filterId))
            }
        }

        writeDb.setTransactionSuccessful()
        writeDb.endTransaction()

        return newQuery
    }

    private fun queryValues(query: Query): ContentValues {
        val values = ContentValues()
        values.put(QUERY_NAME, query.name)
        return values
    }

    private fun filterValues(filter: Filter, query: Query): ContentValues {
        return ContentValues().apply {
            put(FILTER_QUERY_ID, query.id)
            put(FILTER_INDEX, filter.index)
            put(FILTER_TYPE, filter.type.name)
        }
    }

    private fun parameterValues(parameter: FilterParameter, filterId: Long): ContentValues {
        return ContentValues().apply {
            put(FILTER_PARAMETER_FILTER_ID, filterId)
            put(FILTER_PARAMETER_NAME, parameter.name)
            put(FILTER_PARAMETER_STRING_VALUE, parameter.stringValue)
        }
    }

    fun addFeed(feed: Feed) {
        val values = feedValues(feed)
        writeDb.insert(FEEDS_TABLE, null, values)
    }


    private fun feedValues(feed: Feed): ContentValues {
        val values = ContentValues().apply {
            put(FEED_URL, feed.url.toString())
            put(FEED_LAST_UPDATED, feed.lastUpdate.time)
        }
        return values
    }

    fun getResults(): List<Result> {

        val filterId = "filterId"
        val queryId = "queryId"
        val resultId = "resultId"
        val feedId = "feedId"

        var cursor = readDb.rawQuery("select $QUERIES_TABLE.$ID $queryId, " +
                "$FILTER_TABLE.$ID $filterId, $RESULTS_TABLE.$ID $resultId, " +
                "$FEEDS_TABLE.$ID $feedId, * from $RESULTS_TABLE " +
                "join $FEEDS_TABLE on $FEEDS_TABLE.$ID = $RESULTS_TABLE.$RESULT_FEED_ID " +
                "join $QUERIES_TABLE on $QUERIES_TABLE.$ID = $RESULTS_TABLE.$RESULT_QUERY_ID " +
                "join $FILTER_TABLE on $FILTER_TABLE.$FILTER_QUERY_ID = $QUERIES_TABLE.$ID " +
                "join $FILTER_PARAMETER_TABLE on " +
                    "$FILTER_PARAMETER_TABLE.$FILTER_PARAMETER_FILTER_ID = $FILTER_TABLE.$ID",
                null)

        val queries = loadQueries(cursor, filterId, queryId).associateBy { it.id }

        val feeds = HashMap<Long, Feed>()

        if(cursor.moveToFirst()) {
            do {
                val feedId = cursor.getLong(cursor.getColumnIndex(feedId))
                feeds.put(feedId, feed(cursor))
            } while (cursor.moveToNext())
        }


        val results = LinkedList<Result>()
        val resultSet = HashSet<Long>()

        if(cursor.moveToFirst()) {
            do {
                val resultId = cursor.getLong(cursor.getColumnIndex(resultId))
                if(!resultSet.contains(resultId)) {
                    val result = result(cursor, feeds, queries)
                    resultSet.add(resultId)
                    results.add(result)
                }
            } while (cursor.moveToNext())
        }

        return results
    }

    private fun result(cursor: Cursor, feeds: Map<Long, Feed>, queries: Map<Long, Query>): Result {
        val title = cursor.getString(cursor.getColumnIndex(RESULT_FEED_ITEM_TITLE))
        val desc = cursor.getString(cursor.getColumnIndex(RESULT_FEED_ITEM_DESCRIPTION))
        val linkStr = cursor.getString(cursor.getColumnIndex(RESULT_FEED_ITEM_LINK))
        val link = if (linkStr != null) URL(linkStr) else null
        val feedDate = Date(cursor.getLong(cursor.getColumnIndex(RESULT_FEED_ITEM_DATE)))
        val date = Date(cursor.getLong(cursor.getColumnIndex(RESULT_FOUND)))
        val feedName = cursor.getString(cursor.getColumnIndex(RESULT_FEED_NAME))

        val feedId = cursor.getLong(cursor.getColumnIndex(RESULT_FEED_ID))
        val queryId = cursor.getLong(cursor.getColumnIndex(RESULT_QUERY_ID))

        return Result(feeds.get(feedId)!!, queries.get(queryId)!!,
                FeedItem(title, desc, link, feedDate), date, feedName)
    }

    fun updateFeed(feed: Feed) {
        writeDb.update(FEEDS_TABLE, feedValues(feed), "$FEED_URL = ?",
                arrayOf(feed.url.toString()))
    }

    fun addResult(result: Result) {
        writeDb.insert(RESULTS_TABLE, null, resultValues(result))
    }

    fun addResultAndUpdateFeed(result: Result, feed: Feed) {
        writeDb.beginTransaction()
        addResult(result)
        updateFeed(feed)
        writeDb.setTransactionSuccessful()
        writeDb.endTransaction()
    }

    private fun getFeedIdByURL(url: URL): Long? {
        val db = readableDatabase
        val cursor = db.query(FEEDS_TABLE, null, "$FEED_URL = ?", arrayOf(url.toString()),
                null, null, null)

        if(cursor.moveToFirst()) {
            return cursor.getLong(cursor.getColumnIndex(ID))
        } else {
            return null
        }
    }

    private fun resultValues(result: Result): ContentValues {
        val feedId = getFeedIdByURL(result.feed.url)
        return ContentValues().apply {
            put(RESULT_FEED_NAME, result.feedName)
            put(RESULT_QUERY_ID, result.query.id)
            put(RESULT_FEED_ID, feedId)
            put(RESULT_FOUND, result.found.time)
            put(RESULT_FEED_ITEM_TITLE, result.item.title)
            put(RESULT_FEED_ITEM_DESCRIPTION, result.item.description)
            put(RESULT_FEED_ITEM_LINK, result.item.link?.toString())
            put(RESULT_FEED_ITEM_DATE, result.item.date?.time)
        }
    }

    companion object {
        private const val DATABASE_NAME = "feedwatcher.db"
        private const val DATABASE_VERSION = 1

        // Tables
        private const val FEEDS_TABLE = "feeds"
        private const val FILTER_TABLE = "filter"
        private const val FILTER_PARAMETER_TABLE = "filter_parameter"
        private const val QUERIES_TABLE = "queries"
        private const val RESULTS_TABLE = "results"


        //Columns
        private const val ID = "id"

        private const val FEED_URL = "url"
        private const val FEED_LAST_UPDATED = "last_updated"

        private const val FILTER_TYPE = "type"
        private const val FILTER_INDEX = "position"
        private const val FILTER_QUERY_ID = "query_id"

        private const val FILTER_PARAMETER_NAME = "parameter_name"
        private const val FILTER_PARAMETER_STRING_VALUE = "string_value"
        private const val FILTER_PARAMETER_FILTER_ID = "filter_id"

        private const val QUERY_NAME = "name"

        private const val RESULT_FEED_ID = "feed_id"
        private const val RESULT_QUERY_ID = "query_id"
        private const val RESULT_FEED_ITEM_TITLE = "feed_item_title"
        private const val RESULT_FEED_ITEM_DESCRIPTION = "feed_item_description"
        private const val RESULT_FEED_ITEM_LINK = "feed_item_url"
        private const val RESULT_FEED_ITEM_DATE = "feed_item_date"
        private const val RESULT_FOUND = "found"
        private const val RESULT_FEED_NAME = "feed_name"

    }
}