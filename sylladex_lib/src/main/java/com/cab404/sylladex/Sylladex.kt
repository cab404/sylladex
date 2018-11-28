package com.cab404.sylladex

import android.os.Looper
import android.util.Log
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import java.lang.IllegalStateException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dynamic adapter, specialized in recycler views.
 * Because nobody uses anything else anymore, and proxy classes in
 * Chumroll does not look as cute as I want them to be.
 *
 * Also it's a lot better at retrieval by id and is keeping less references to stuff.
 * Also it can move stuff around.
 */
class Sylladex : RecyclerView.Adapter<Sylladex.VH>()/*, Parcelable*/ {
//
//    private companion object {
//        @JvmField
//        val CREATOR = object : Parcelable.Creator<Sylladex> {
//            override fun createFromParcel(source: Parcel): Sylladex {
//                val target = Sylladex()
//                val typeCount = source.readInt()
//                repeat(typeCount) {
//
//                }
//                val itemCount = source.readInt()
//                repeat(itemCount)
//            }
//
//            override fun newArray(size: Int): Array<Sylladex?> = Array(size) { null }
//        }
//    }
//
//    override fun writeToParcel(dest: Parcel?, flags: Int) {
//    }
//
//    override fun describeContents(): Int {
//    }

    /**
     * Describes how to create view for [DataType], and how to fill it with information.
     * */
    interface ItemType<DataType> {
        /** Called to create an initial view. */
        fun createView(parent: ViewGroup, inflater: LayoutInflater, adapter: Sylladex): View

        /** Called to insert data into view, created in [createView]. */
        fun bindData(view: View, data: DataType, binding: Binding)

        /** Returns bound [PlaceInfo] */
        @Suppress("UNCHECKED_CAST")
        val Binding.info
            get() = sylladex.getById(id) as PlaceInfo<DataType>

    }

    /** Stores info about added item in this adapter. */
    inner class PlaceInfo<DataType>(
        val data: DataType,
        val typeId: Int,
        val id: Int = mItemIdInc.incrementAndGet()
    ) {
        val type get() = mTypes[typeId]

        init {
            mItemIdAssoc.put(id, this)
            mTypeUseCount.put(typeId, mTypeUseCount[typeId] + 1)
        }

        /** Removes id and type associations to this item. */
        internal fun unbind() {
            mItemIdAssoc.delete(id)
            // we'll remove type if it's not being used.
            val useCount = mTypeUseCount[typeId] - 1
            mTypeUseCount.put(typeId, useCount)
            if (useCount == 0) mTypes.remove(typeId)
        }

    }

    /** Stores info about current ViewHolder/PlaceInfo pair. Try not to leak it. */
    inner class Binding(
        /** [ID][PlaceInfo.id] of this item. */
        val id: Int
    ) {
        /** Reference to current Sylladex */
        val sylladex get() = this@Sylladex
        /** Returns current position index of this item. Will be -1 if item is not currently bound to ViewHolder. */
        val index
            get() = mItems.indexOfFirst { it.id == id }.let {
                if (it == -1) null
                else it
            }

        fun delete() = index?.also { remove(it) }?.let { true } ?: false
    }

    /** registered data set observers */
    private val mObservables = mutableListOf<RecyclerView.AdapterDataObserver>()

    /** previous type id value */
    private val mTypeIdInc = AtomicInteger()
    /** number of users for each type */
    private val mTypeUseCount = SparseIntArray()
    /** currently registered item types */
    private val mTypes = SparseArray<ItemType<*>>()

    /** id to PlaceInfo associations for faster item retrieval */
    private val mItemIdAssoc = SparseArray<PlaceInfo<*>>()
    /** previous place id value */
    private val mItemIdInc = AtomicInteger()

    /** underlying item list*/
    private val mItems: MutableList<PlaceInfo<*>> = mutableListOf()

    //  ==============  \\
    // == CRUD START == \\
    //  ==============  \\

    private fun checkThread() {
        if (Thread.currentThread() != Looper.getMainLooper().thread && hasObservers())
            throw IllegalStateException("This adapter is attached; can only be edited in Main Thread.")
    }

    val items get() = mItems.toList()
    /** Registers (if necessary) and returns typeIds of given ItemTypes */
    private fun getTypeIds(vararg types: ItemType<*>): List<Int> =
        types
            .map {
                val index = mTypes.indexOfValue(it)
                if (index < 0)
                    mTypeIdInc.incrementAndGet().also { newId ->
                        mTypes.append(newId, it)
                    }
                else
                    mTypes.keyAt(index)
            }

    /** Returns PlaceInfo by its id. This method is O(1). */
    fun getById(id: Int) = mItemIdAssoc[id]

    /** Returns PlaceInfo by its position */
    fun getByIndex(index: Int) = mItems[index]

    /** Appends new items to given index. */
    @Suppress("UNCHECKED_CAST")
    fun <DataType> add(what: List<DataType>, type: ItemType<DataType>, at: Int = itemCount) {
        checkThread()
        val (typeId) = getTypeIds(type)
        mItems.addAll(
            index = at,
            elements = what
                .reversed()
                .map { data -> PlaceInfo(data, typeId) }
        )
        mObservables.forEach { observer ->
            observer.onItemRangeInserted(at, what.size)
        }
    }

    /** Just a convenient way of calling [add] with a single item */
    fun <DataType> add(what: DataType, type: ItemType<DataType>, at: Int = itemCount) {
        add(listOf(what), type, at)
    }

    /** Removes a range of items */
    fun remove(at: Int, count: Int = 1) {
        checkThread()
        ((at + count - 1) downTo at).forEach {
            mItems.removeAt(it).unbind()
        }
        mObservables.forEach { observer ->
            observer.onItemRangeRemoved(at, count)
        }
    }

    /** Clears item list */
    fun clear() = remove(0, itemCount)

    /** Updates a range of items with new ones */
    fun <DataType> replace(what: List<DataType>, type: ItemType<DataType>, at: Int = 1) {
        checkThread()
        ((at + what.size - 1) downTo at).forEach {
            mItems.removeAt(it).unbind()
        }
        val (typeId) = getTypeIds(type)
        mItems.addAll(
            index = at,
            elements = what
                .reversed()
                .map { data -> PlaceInfo(data, typeId) }
        )
        mObservables.forEach { observer ->
            observer.onItemRangeChanged(at, what.size, what)
        }
    }

    /** Convenient way of calling [replace] with a single item */
    fun <DataType> replace(what: DataType, type: ItemType<DataType>, at: Int = itemCount) {
        replace(listOf(what), type, at)
    }

    /** Moves segment of a list to somewhere else */
    fun move(at: Int, to: Int, count: Int = 1) {
        checkThread()

        val offset = to - at
        // everything is already in place
        if (offset == 0) return

        val endIndex = at + count - 1
        // if we are moving sector forward, then we'll need to go from end to start to
        // retain correct indexing while adding offset, otherwise we'll got from start to end
        val moveOrder = if (offset > 0) endIndex..at else at..endIndex
        moveOrder.forEach { i -> mItems.add(i + offset, mItems.removeAt(i)) }

        mObservables.forEach { observer -> observer.onItemRangeMoved(at, to, count) }
    }

    //  ======================  \\
    // == ADAPTER IMPL START == \\
    //  ======================  \\


    /** Simplest view holder there is. */
    inner class VH(val view: View, val type: ItemType<Any?>) : RecyclerView.ViewHolder(view)

    @Suppress("UNCHECKED_CAST")
    override fun onCreateViewHolder(parent: ViewGroup, type: Int): VH {
        return (mTypes[type] as ItemType<Any?>).let {
            VH(
                type = it,
                view = it.createView(
                    parent = parent,
                    inflater = LayoutInflater.from(parent.context),
                    adapter = this
                )
            )
        }
    }

    override fun getItemCount(): Int = mItems.size

    override fun getItemViewType(position: Int): Int = mItems[position].typeId

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = mItems[position].id.toLong()

    override fun onBindViewHolder(holder: VH, index: Int) {
        val info = mItems[index]
        holder.type.bindData(
            holder.view,
            info.data,
            Binding(info.id)
        )
    }

    override fun registerAdapterDataObserver(observer: RecyclerView.AdapterDataObserver) {
        super.registerAdapterDataObserver(observer)
        mObservables += observer
    }

    override fun unregisterAdapterDataObserver(observer: RecyclerView.AdapterDataObserver) {
        super.unregisterAdapterDataObserver(observer)
        mObservables -= observer
    }

}