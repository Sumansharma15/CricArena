package com.example.cricarena.ui.mymatches

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cricarena.R
import com.example.cricarena.data.model.MyMatchItem
import com.example.cricarena.databinding.FragmentMyMatchesListBinding
import com.example.cricarena.ui.matchdetails.MatchDetailsActivity

class MyMatchesListFragment : Fragment() {

    private var _binding: FragmentMyMatchesListBinding? = null
    private val binding get() = _binding ?: error("Binding accessed outside view lifecycle.")
    private val viewModel: MyMatchesViewModel by viewModels()
    private lateinit var tab: MyMatchesTab

    private val adapter = MyMatchItemAdapter { match ->
        openMatchDetails(match)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val type = requireArguments().getString(ARG_TAB_TYPE).orEmpty()
        tab = runCatching { MyMatchesTab.valueOf(type) }.getOrDefault(MyMatchesTab.JOINED)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyMatchesListBinding.inflate(inflater, container, false)
        setupRecycler()
        bindObservers()
        viewModel.setTab(tab)
        viewModel.observeMatches()
        return binding.root
    }

    private fun setupRecycler() {
        binding.recyclerMyMatches.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@MyMatchesListFragment.adapter
        }
    }

    private fun bindObservers() {
        viewModel.items.observe(viewLifecycleOwner) { items ->
            render(items)
        }
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            setLoading(isLoading)
        }
        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun render(items: List<MyMatchItem>) {
        adapter.submitList(items)
        binding.textEmpty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setLoading(isLoading: Boolean) {
        binding.progressMyMatches.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun openMatchDetails(match: MyMatchItem) {
        val intent = Intent(requireContext(), MatchDetailsActivity::class.java).apply {
            putExtra(MatchDetailsActivity.EXTRA_MATCH_ID, match.matchId)
            putExtra(MatchDetailsActivity.EXTRA_MATCH_NAME, match.matchName)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerMyMatches.adapter = null
        _binding = null
    }

    companion object {
        private const val ARG_TAB_TYPE = "arg_tab_type"

        fun newInstance(tab: MyMatchesTab): MyMatchesListFragment {
            return MyMatchesListFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TAB_TYPE, tab.name)
                }
            }
        }
    }
}
