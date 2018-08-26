package me.murks.podcastwatcher.activities

import android.app.Activity
import android.content.Intent
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBar
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import me.murks.podcastwatcher.PodcastWatcherApp
import me.murks.podcastwatcher.R
import me.murks.podcastwatcher.activities.QueriesFragment.OnListFragmentInteractionListener
import me.murks.podcastwatcher.model.Query

class OverviewActivity : AppCompatActivity(), OnListFragmentInteractionListener {

    private lateinit var mDrawerLayout: DrawerLayout
    private lateinit var app: PodcastWatcherApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app = PodcastWatcherApp()

        setContentView(R.layout.activity_overview)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        val actionbar: ActionBar? = supportActionBar
        actionbar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_menu)
        }

        val navigationView: NavigationView = findViewById(R.id.nav_view)
        navigationView.setNavigationItemSelectedListener { menuItem ->
            mDrawerLayout.closeDrawers()

            when(menuItem.itemId) {
                R.id.nav_feeds -> {
                    val transaction = supportFragmentManager.beginTransaction()
                    transaction.replace(R.id.overview_fragment_container, FeedsFragment())
                            .addToBackStack(null)
                    transaction.commit()
                }
                R.id.nav_queries -> {
                    val transaction = supportFragmentManager.beginTransaction()
                    transaction.replace(R.id.overview_fragment_container, QueriesFragment())
                            .addToBackStack(null)
                    transaction.commit()
                }
                R.id.nav_add_query -> {
                    val intent = Intent(this, QueryActivity::class.java)
                    startActivityForResult(intent, NEW_QUERY_REQUEST)
                }
            }

            true
        }


        mDrawerLayout = findViewById(R.id.drawer_layout)

        val transaction = supportFragmentManager.beginTransaction()
        transaction.replace(R.id.overview_fragment_container, QueriesFragment())
        transaction.commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                mDrawerLayout.openDrawer(GravityCompat.START)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onListFragmentInteraction(item: Query) {
        val intent = Intent(this, QueryActivity::class.java)
        intent.putExtra(QueryActivity.INTENT_QUERY_EXTRA, item)
        startActivityForResult(intent, EDIT_QUERY_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == EDIT_QUERY_REQUEST && resultCode == Activity.RESULT_OK) {
            val query = data!!.getParcelableExtra<Query>(QueryActivity.INTENT_QUERY_EXTRA)
            app.updateQuery(query)
        }
        if(requestCode == NEW_QUERY_REQUEST && resultCode == Activity.RESULT_OK) {
            val query = data!!.getParcelableExtra<Query>(QueryActivity.INTENT_QUERY_EXTRA)
            app.addQuery(query)
        }
    }

    companion object {
        const val NEW_QUERY_REQUEST = 22
        const val EDIT_QUERY_REQUEST = 101
    }
}
