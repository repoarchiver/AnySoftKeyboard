/*
 * Copyright (c) 2016 Menny Even-Danan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.anysoftkeyboard.ui.settings;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.Fragment;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.anysoftkeyboard.addons.AddOn;
import com.anysoftkeyboard.addons.AddOnsFactory;
import com.anysoftkeyboard.keyboards.views.DemoAnyKeyboardView;
import com.anysoftkeyboard.ui.settings.widget.AddOnStoreSearchView;
import com.anysoftkeyboard.utils.Logger;
import com.menny.android.anysoftkeyboard.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class AbstractKeyboardAddOnsBrowserFragment<E extends AddOn> extends Fragment {

    private final List<CharSequence> mEnabledAddOnsIds = new ArrayList<>();
    @NonNull
    private final String mLogTag;
    @StringRes
    private final int mFragmentTitleResId;
    private final boolean mIsSingleSelection;
    private final boolean mSimulateTyping;
    private final boolean mHasTweaksOption;
    private AddOnsFactory<E> mFactory;
    private List<E> mAllAddOns;
    private final ItemTouchHelper.Callback mItemTouchCallback = new ItemTouchHelper.SimpleCallback(getItemDragDirectionFlags(), 0) {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            final int to = target.getAdapterPosition();
            final int from = viewHolder.getAdapterPosition();
            E temp = ((KeyboardAddOnViewHolder) viewHolder).mAddOn;
            //anything that is dragged, must be enabled
            if (mEnabledAddOnsIds.contains(temp.getId())) {
                //first removing from the old position
                if (temp.getId().equals(mEnabledAddOnsIds.remove(from))) {
                    throw new IllegalStateException("from value does not contain the dragged item!");
                }
            }

            mEnabledAddOnsIds.add(to, temp.getId());
            Collections.swap(mAllAddOns, from, to);
            recyclerView.getAdapter().notifyItemMoved(from, to);
            //making sure `to` is visible
            recyclerView.scrollToPosition(to);
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

        }
    };
    private final ItemTouchHelper mRecyclerViewItemTouchHelper = new ItemTouchHelper(mItemTouchCallback);
    private RecyclerView mRecyclerView;
    private int mPreviousSingleSelectedItem = -1;
    @Nullable
    private DemoAnyKeyboardView mSelectedKeyboardView;
    private int mColumnsCount = 2;

    protected AbstractKeyboardAddOnsBrowserFragment(@NonNull String logTag, @StringRes int fragmentTitleResId, boolean isSingleSelection, boolean simulateTyping, boolean hasTweaksOption) {
        if (isSingleSelection && (getItemDragDirectionFlags() != 0))
            throw new IllegalStateException("Does not support drag operations (and order) with a single selection list");

        mLogTag = logTag;
        mIsSingleSelection = isSingleSelection;
        mSimulateTyping = simulateTyping;
        mHasTweaksOption = hasTweaksOption;
        if (mSimulateTyping && !mIsSingleSelection)
            throw new IllegalStateException("only supporting simulated-typing in single-selection setup!");
        mFragmentTitleResId = fragmentTitleResId;
        setHasOptionsMenu(mHasTweaksOption || getMarketSearchTitle() != 0);
    }

    @NonNull
    protected abstract AddOnsFactory<E> getAddOnFactory();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mFactory = getAddOnFactory();
        mColumnsCount = getResources().getInteger(R.integer.add_on_items_columns);
    }

    @Override
    public View onCreateView(LayoutInflater paramLayoutInflater, ViewGroup paramViewGroup, Bundle paramBundle) {
        return paramLayoutInflater.inflate(mIsSingleSelection ? R.layout.add_on_browser_single_selection_layout : R.layout.add_on_browser_multiple_selection_layout, paramViewGroup, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context appContext = getActivity().getApplicationContext();

        mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
        mRecyclerView.setHasFixedSize(false);
        mRecyclerView.setLayoutManager(createLayoutManager(appContext));
        mRecyclerView.setAdapter(new DemoKeyboardAdapter());
        mRecyclerViewItemTouchHelper.attachToRecyclerView(mRecyclerView);

        if (mIsSingleSelection) {
            mSelectedKeyboardView = (DemoAnyKeyboardView) view.findViewById(R.id.selected_demo_keyboard_view);
            if (mSimulateTyping) {
                mSelectedKeyboardView.setSimulatedTypingText("welcome to anysoftkeyboard");
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (hasOptionsMenu()) {
            inflater.inflate(R.menu.add_on_selector_menu, menu);
            menu.findItem(R.id.add_on_market_search_menu_option).setVisible(getMarketSearchTitle() != 0);
            menu.findItem(R.id.tweaks_menu_option).setVisible(mHasTweaksOption);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.tweaks_menu_option:
                onTweaksOptionSelected();
                return true;
            case R.id.add_on_market_search_menu_option:
                AddOnStoreSearchView.startMarketActivity(getContext(), getMarketSearchKeyword());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected void onTweaksOptionSelected() {
    }

    protected int getItemDragDirectionFlags() {
        return 0;
    }

    @Override
    public void onStart() {
        super.onStart();
        //I need a mutable list.
        final List<E> immutableAddOnsList = mFactory.getAllAddOns();
        mAllAddOns = new ArrayList<>(immutableAddOnsList.size());

        mEnabledAddOnsIds.clear();
        for (E addOn : mFactory.getOrderedEnabledAddOns()) {
            mEnabledAddOnsIds.add(addOn.getId());
            mAllAddOns.add(addOn);
            if (mIsSingleSelection && mSelectedKeyboardView != null)
                applyAddOnToDemoKeyboardView(addOn, mSelectedKeyboardView);
        }
        //adding disabled addons
        for (E addOn : immutableAddOnsList) {
            if (!mEnabledAddOnsIds.contains(addOn.getId())) mAllAddOns.add(addOn);
        }
        Logger.d(mLogTag, "Got %d available addons and %d enabled addons", mAllAddOns.size(), mEnabledAddOnsIds.size());
        mRecyclerView.getAdapter().notifyDataSetChanged();
        MainSettingsActivity.setActivityTitle(this, getString(mFragmentTitleResId));
    }

    @NonNull
    private RecyclerView.LayoutManager createLayoutManager(@NonNull Context appContext) {
        GridLayoutManager manager = new GridLayoutManager(appContext, mColumnsCount, LinearLayoutManager.VERTICAL, false);
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (mAllAddOns != null && position == mAllAddOns.size()) return mColumnsCount;
                else return 1;
            }
        });

        return manager;
    }

    protected abstract void applyAddOnToDemoKeyboardView(@NonNull final E addOn, @NonNull final DemoAnyKeyboardView demoKeyboardView);

    @Nullable
    protected abstract String getMarketSearchKeyword();

    @StringRes
    protected abstract int getMarketSearchTitle();

    private class KeyboardAddOnViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final DemoAnyKeyboardView mDemoKeyboardView;
        private final ImageView mAddOnEnabledView;
        private final TextView mAddOnTitle;
        private final TextView mAddOnDescription;
        private E mAddOn;

        public KeyboardAddOnViewHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);
            mDemoKeyboardView = (DemoAnyKeyboardView) itemView.findViewById(R.id.demo_keyboard_view);
            mAddOnEnabledView = (ImageView) itemView.findViewById(R.id.enabled_image);
            mAddOnTitle = (TextView) itemView.findViewById(R.id.title);
            mAddOnDescription = (TextView) itemView.findViewById(R.id.subtitle);
        }

        private void bindToAddOn(@NonNull E addOn) {
            mAddOn = addOn;
            mAddOnTitle.setText(addOn.getName());
            mAddOnDescription.setText(addOn.getDescription());
            final boolean isEnabled = mEnabledAddOnsIds.contains(addOn.getId());
            mAddOnEnabledView.setVisibility(isEnabled ? View.VISIBLE : View.INVISIBLE);
            mAddOnEnabledView.setImageResource(isEnabled ? R.drawable.ic_accept : R.drawable.ic_cancel);
            applyAddOnToDemoKeyboardView(addOn, mDemoKeyboardView);
        }

        @Override
        public void onClick(View v) {
            final boolean isEnabled = mEnabledAddOnsIds.contains(mAddOn.getId());
            if (mIsSingleSelection) {
                if (isEnabled || mSelectedKeyboardView == null) return;
                mEnabledAddOnsIds.clear();
                mEnabledAddOnsIds.add(mAddOn.getId());
                applyAddOnToDemoKeyboardView(mAddOn, mSelectedKeyboardView);
            } else {
                if (isEnabled) {
                    mEnabledAddOnsIds.remove(mAddOn.getId());
                    //moving to the end of the list
                    Collections.swap(mAllAddOns, getAdapterPosition(), mEnabledAddOnsIds.size());
                } else {
                    mEnabledAddOnsIds.add(mAddOn.getId());
                    //moving to the end of the list
                    Collections.swap(mAllAddOns, getAdapterPosition(), mEnabledAddOnsIds.size() - 1);
                }
            }

            mFactory.setOrderedEnabledIds(mEnabledAddOnsIds);

            if (mIsSingleSelection) {
                //also notifying about the previous item being automatically unselected
                if (mPreviousSingleSelectedItem == -1)
                    mRecyclerView.getAdapter().notifyDataSetChanged();
                else mRecyclerView.getAdapter().notifyItemChanged(mPreviousSingleSelectedItem);
                mPreviousSingleSelectedItem = getAdapterPosition();
            }
            mRecyclerView.getAdapter().notifyItemChanged(getAdapterPosition());
        }
    }

    private class DemoKeyboardAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final LayoutInflater mLayoutInflater;

        DemoKeyboardAdapter() {
            mLayoutInflater = LayoutInflater.from(getActivity());
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == 0) {
                View itemView = mLayoutInflater.inflate(R.layout.add_on_browser_view_item, parent, false);
                return new KeyboardAddOnViewHolder(itemView);
            } else {
                AddOnStoreSearchView searchView = new AddOnStoreSearchView(getActivity(), null);
                searchView.setTag(getMarketSearchKeyword());
                searchView.setTitle(getText(getMarketSearchTitle()));
                return new RecyclerView.ViewHolder(searchView) {/*empty implementation*/
                };
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            E addOn = mAllAddOns.get(position);
            ((KeyboardAddOnViewHolder) holder).bindToAddOn(addOn);
        }

        @Override
        public int getItemViewType(int position) {
            if (mAllAddOns != null && position == mAllAddOns.size()) return 1;
            else return 0;
        }

        @Override
        public int getItemCount() {
            final int extra = getMarketSearchKeyword() != null ? 1 : 0;
            return (mAllAddOns == null ? 0 : mAllAddOns.size()) + extra;
        }
    }
}