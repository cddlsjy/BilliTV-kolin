package com.bili.tv.bili_tv_app.screens.category

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bili.tv.bili_tv_app.R

/**
 * 分类适配器
 */
class CategoryAdapter(
    private val onCategoryClick: (CategoryFragment.CategoryZone) -> Unit
) : ListAdapter<CategoryFragment.CategoryZone, CategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    private var selectedRid: Int = 1

    fun setSelectedRid(rid: Int) {
        val oldRid = selectedRid
        selectedRid = rid
        // 刷新选中的项
        currentList.forEachIndexed { index, category ->
            if (category.rid == oldRid || category.rid == rid) {
                notifyItemChanged(index)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        holder.bind(getItem(position), selectedRid)
    }

    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.category_name)

        fun bind(category: CategoryFragment.CategoryZone, selectedRid: Int) {
            nameText.text = category.name

            // 高亮选中项
            if (category.rid == selectedRid) {
                itemView.setBackgroundResource(R.drawable.category_selected_bg)
                nameText.setTextColor(itemView.context.getColor(R.color.primary))
            } else {
                itemView.setBackgroundResource(R.drawable.category_normal_bg)
                nameText.setTextColor(itemView.context.getColor(R.color.text_primary))
            }

            itemView.setOnClickListener {
                onCategoryClick(category)
            }

            itemView.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    itemView.scaleX = 1.1f
                    itemView.scaleY = 1.1f
                } else {
                    itemView.scaleX = 1.0f
                    itemView.scaleY = 1.0f
                }
            }
        }
    }

    class CategoryDiffCallback : DiffUtil.ItemCallback<CategoryFragment.CategoryZone>() {
        override fun areItemsTheSame(
            oldItem: CategoryFragment.CategoryZone,
            newItem: CategoryFragment.CategoryZone
        ): Boolean = oldItem.rid == newItem.rid

        override fun areContentsTheSame(
            oldItem: CategoryFragment.CategoryZone,
            newItem: CategoryFragment.CategoryZone
        ): Boolean = oldItem == newItem
    }
}
