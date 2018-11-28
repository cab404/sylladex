package com.cab404.sylladex

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_list.*

class TestActivity : AppCompatActivity() {

    class TestAdapter(val clicker: (Int, Sylladex.Binding) -> Unit) : Sylladex.ItemType<Int> {
        lateinit var adapter: Sylladex

        override fun createView(parent: ViewGroup, inflater: LayoutInflater, adapter: Sylladex): View {
            this.adapter = adapter
            return inflater.inflate(R.layout.item_test, parent, false)
        }

        override fun bindData(view: View, data: Int, binding: Sylladex.Binding) {
            view.findViewById<TextView>(R.id.vText)
                .setText(
                    """[$data],
                        |itemId: ${binding.id}
                        |registered as type ${binding.info.typeId}
                        |index since last `bindData`: ${binding.index}""".trimMargin()
                )
            view.setOnClickListener {
                binding.index?.also { index -> clicker(index, binding) }
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        val syl = Sylladex()
        lateinit var adapter: TestAdapter
        lateinit var a2apter: TestAdapter
        adapter = TestAdapter { it, bind ->
            syl.replace(listOf(it + 1000), a2apter, it)
        }
        a2apter = TestAdapter { it, bind ->
            syl.add(it - 200, adapter, it + 1)
        }

        vList.adapter = syl
        vList.layoutManager = LinearLayoutManager(baseContext)

        syl.add(List(1, { it }), type = adapter)

    }

}