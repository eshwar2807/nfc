package com.example.nfcwallet

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.DateFormat
import java.util.Date

class ProfileAdapter(
    private var items: List<CardProfile>,
    private var activeId: String?,
    private val onActivate: (CardProfile) -> Unit,
    private val onDelete: (CardProfile) -> Unit
) : RecyclerView.Adapter<ProfileAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.profileName)
        val detail: TextView = view.findViewById(R.id.profileDetail)
        val activate: Button = view.findViewById(R.id.activateButton)
        val delete: Button = view.findViewById(R.id.deleteButton)
    }

    fun update(newItems: List<CardProfile>, newActiveId: String?) {
        items = newItems
        activeId = newActiveId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_profile, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = items[position]
        val active = p.id == activeId
        holder.name.text = if (active) "★ ${p.name} (active)" else p.name

        val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(p.capturedAt))
        val tech = p.techList.joinToString(", ") { it.substringAfterLast('.') }
        val emu = if (p.emulationSupported())
            "emulation: supported (ISO-DEP)"
        else
            "emulation: not on stock phone (UID-based)"
        holder.detail.text = "UID ${p.uidHex}\n$tech\n$emu\n$date"

        holder.activate.text = if (active) "Active" else "Use this card"
        holder.activate.isEnabled = !active
        holder.activate.setOnClickListener { onActivate(p) }
        holder.delete.setOnClickListener { onDelete(p) }
    }
}
