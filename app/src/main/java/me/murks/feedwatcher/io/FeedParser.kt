/*
This file is part of FeedWatcher.

FeedWatcher is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

FeedWatcher is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with FeedWatcher. If not, see <https://www.gnu.org/licenses/>.
Copyright 2020 Zouroboros
 */
package me.murks.feedwatcher.io

import me.murks.feedwatcher.model.FeedItem
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Class for loading data from rss or atom feeds.
 * @author zouroboros
 */
class FeedParser(inputStream: InputStream, parser: XmlPullParser) {

    init {
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)
    }

    private var feedName: String? = null
    private var feedDescription: String? = null
    private var feedIconUrl: URL? = null
    private val entries = LinkedList<FeedItem>()

    private var itemTitle: String? = null
    private var itemDescription: String? = null
    private var itemLink: String? = null
    private var itemDate: Date? = null

    private val states = listOf(
            ParserNode("rss", {} ,
                listOf(ParserNode("channel", {},
                        listOf(
                            ParserNode("title", { p -> feedName = p.nextText() }),
                            ParserNode("description", { p -> feedDescription = p.nextText()}),
                            ParserNode("itunes:summary", { p -> feedDescription = p.nextText()}),
                            ParserNode("image", {}, listOf(ParserNode("url", { p -> feedIconUrl = URL(p.nextText())}))),
                            ParserNode("itunes:image", { p -> feedIconUrl = URL(p.getAttributeValue(null, "href"))}),
                            ParserNode("item", { p ->
                                    if(p.eventType == XmlPullParser.END_TAG && p.name == "item") {
                                        entries.add(FeedItem(itemTitle!!, itemDescription!!, if (itemLink != null) URL(itemLink) else null, itemDate!!))
                                    }
                                }, listOf(
                                    ParserNode("title", { p -> itemTitle = p.nextText()}),
                                    ParserNode("description", { p ->
                                        itemDescription = p.nextText()}),
                                    ParserNode("link", { p -> itemLink = p.nextText()}),
                                    ParserNode("pubDate", { p -> itemDate = tryReadDate(p.nextText())}))))))),
            ParserNode("feed", {}, listOf(
                    ParserNode("title", { p -> feedName = p.nextText() }),
                    ParserNode("subtitle", { p -> feedDescription = p.nextText()}),
                    ParserNode("icon", { p -> feedIconUrl = URL(p.nextText())}),
                    ParserNode("logo", { p -> feedIconUrl = URL(p.nextText())}),
                    ParserNode("entry", { p ->
                            if(p.eventType == XmlPullParser.END_TAG && p.name == "entry") {
                                entries.add(FeedItem(itemTitle!!, itemDescription!!, if (itemLink != null) URL(itemLink) else null, itemDate!!))
                            }
                        }, listOf(ParserNode("title", { p -> itemTitle = p.nextText()}),
                                ParserNode("summary", { p -> itemDescription = p.nextText()}),
                                ParserNode("media:description", { p -> itemDescription = p.nextText()}),
                                ParserNode("link", { p ->
                                    if(p.eventType == XmlPullParser.START_TAG && p.name == "link") {
                                        itemLink = p.getAttributeValue(null, "href")
                                    }}),
                                ParserNode("published", { p -> itemDate = tryReadDate(p.nextText())}))))))

    private val parser = LazyParser(parser, states)

    val name: String
        get() {
            if(feedName == null) {
                parser.parseUntil { feedName != null }
            }

            return feedName!!
        }

    val iconUrl: URL?
        get() {
            if(feedIconUrl == null) {
                parser.parseUntil { feedIconUrl != null }
            }

            return feedIconUrl
        }

    val description: String?
        get() {
            if(feedDescription == null) {
                parser.parseUntil { feedDescription != null }
            }

            return feedDescription
        }

    fun items(since: Date): List<FeedItem> {
        parser.parseUntil { false }
        return entries.filter { it.date.after(since) }
    }

    private fun tryReadDate(dateStr: String): Date {

        // replace nonstandard time zone identifier
        val str = dateStr.replace("UT", "UTC").replace("Z", "UTC")

        val formats = listOf(SimpleDateFormat("EEE, dd MMM yy HH:mm:ss z", Locale.ENGLISH),
                SimpleDateFormat("EEE, dd MMM yy HH:mm z"),
                SimpleDateFormat("dd MMM yy HH:mm:ss z"),
                SimpleDateFormat("dd MMM yy HH:mm z"),
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
        )

        for (format in formats) {
            try {
                return format.parse(str.trim())!!
            } catch (e: ParseException) { }
        }

        throw ParseException(str, 0)
    }
}