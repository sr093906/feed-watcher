package me.murks.podcastwatcher.activities

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import kotlinx.android.synthetic.main.fragment_queries.view.*
import me.murks.podcastwatcher.R


import me.murks.podcastwatcher.activities.QueriesFragment.OnListFragmentInteractionListener

import me.murks.podcastwatcher.model.Query


class QueryRecyclerViewAdapter(
        private val queries: List<Query>,
        private val listener: OnListFragmentInteractionListener?)
    : RecyclerView.Adapter<QueryRecyclerViewAdapter.ViewHolder>() {

    private val onClickListener: View.OnClickListener

    init {
        onClickListener = View.OnClickListener { v ->
            val item = v.tag as Query
            listener?.onListFragmentInteraction(item)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_queries, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = queries[position]
        holder.name.text = item.name

        with(holder.mView) {
            tag = item
            setOnClickListener(onClickListener)
        }
    }

    override fun getItemCount(): Int = queries.size

    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {
        val name: TextView = mView.query_name

        override fun toString(): String {
            return super.toString() + " '" + name.text + "'"
        }
    }
}
