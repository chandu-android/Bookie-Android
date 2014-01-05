package us.bmark.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import us.bmark.android.prefs.SharedPrefsBackedUserSettings;
import us.bmark.android.utils.ErrorHandler;
import us.bmark.android.utils.IntentConstants;
import us.bmark.android.utils.JustDisplayToastErrorHandler;
import us.bmark.bookieclient.BookieService;
import us.bmark.bookieclient.BookieServiceUtils;
import us.bmark.bookieclient.Bookmark;
import us.bmark.bookieclient.BookmarkList;
import us.bmark.bookieclient.SearchResult;

import static java.net.URLEncoder.encode;
import static us.bmark.android.utils.Utils.isBlank;

public class AllBookMarksListFragment extends ListFragment {

    private static final String TAG = AllBookMarksListFragment.class.getName();
    private int countPP;
    private BookieService service;
    private UserSettings settings;
    private List<Bookmark> bmarks =
            new ArrayList<Bookmark>();
    private String searchTerms;
    private int pagesLoaded;
    private State state = State.ALL;
    private BookmarkArrayAdapter adapter;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    private ErrorHandler errorHandler;

    private View view;
    private enum State {
        ALL, SEARCH
    }

    private class BookmarkArrayAdapter extends ArrayAdapter<Bookmark> {

        private static final int ROW_VIEW_ID = R.layout.allbookmarks_list_item;

        BookmarkArrayAdapter(Context context, List<Bookmark> objects) {
            super(context, ROW_VIEW_ID, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            View row = convertView;
            Bookmark bmark = this.getItem(position);

            if (row == null) {
                LayoutInflater inflater = ((Activity) this.getContext()).getLayoutInflater();
                row = inflater.inflate(ROW_VIEW_ID, parent, false);
            }

            TextView textView = (TextView) row.findViewById(R.id.bookmarkListRowTextView);
            final String description = isBlank(bmark.description) ? bmark.url : bmark.description;
            textView.setText(description);

            return row;
        }
    }

    private class EndlessScrollListener implements AbsListView.OnScrollListener {
        private final int threshold = countPP / 5;
        private int previousTotal = 0;
        private boolean loading = true;

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem,
                             int visibleItemCount, int totalItemCount) {
            if (loading) {
                if (totalItemCount > previousTotal) {
                    loading = false;
                    previousTotal = totalItemCount;
                }
            }
            if (!loading && ((totalItemCount - visibleItemCount) <= (firstVisibleItem + threshold))) {
                loadMoreData();
                loading = true;
            }
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
        }
    }

    private class ServiceCallback implements Callback<BookmarkList> {

        @Override
        public void success(BookmarkList bookmarkList, Response response) {
            getActivity().setProgressBarIndeterminateVisibility(false);
            bmarks.addAll(bookmarkList.bmarks);
            Log.w(TAG, "on success for bookmark list, fetched " + bmarks.size());
            adapter.notifyDataSetChanged();
            pagesLoaded++;
        }

        @Override
        public void failure(RetrofitError error) {
            getActivity().setProgressBarIndeterminateVisibility(false);
            errorHandler.handleError(error);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = inflater.inflate(R.layout.allbook_marks_main, container, false);
        settings = new SharedPrefsBackedUserSettings(getActivity());
        errorHandler = new JustDisplayToastErrorHandler(getActivity(),settings);
        countPP = getResources().getInteger(R.integer.default_number_of_bookmarks_to_get);
        setUpService();
        adapter = new BookmarkArrayAdapter(getActivity(),bmarks);
        setListAdapter(adapter);
        loadMoreData();
        return view;
    }

    private void setUpService() {
        String serverUrl = settings.getBaseUrl();
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setServer(serverUrl).build();
        restAdapter.setLogLevel(RestAdapter.LogLevel.FULL);
        service = restAdapter.create(BookieService.class);
    }

    private void refreshWithNewestGlobal() {
        int nextPage = pagesLoaded;
        getActivity().setProgressBarIndeterminateVisibility(true);
        service.everyonesRecent(countPP, nextPage, new ServiceCallback());
    }

    @Override
    public void onStart() {
        super.onStart();
        setUpListView();
    }

    private void setUpListView() {
        ListView lv = getListView();

        lv.setTextFilterEnabled(true);

        lv.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                final Bookmark bmark = ((Bookmark) parent.getAdapter().getItem(position));

                final Uri uri = Uri.parse(BookieServiceUtils.urlForRedirect(bmark,
                        settings.getBaseUrl(),
                        settings.getUsername()));
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            }
        });

        lv.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                                           int position, long id) {
                final Bookmark bmark = ((Bookmark) parent.getAdapter().getItem(position));
                final Bundle bundle = new Bundle();
                String bmarkJson = (new Gson()).toJson(bmark);
                bundle.putString(IntentConstants.EXTRAS_KEY_BMARK, bmarkJson);
                final Intent intent = new Intent(getActivity(),
                        BookMarkDetailActivity.class);
                intent.putExtras(bundle);
                startActivity(intent);
                return true;
            }
        });

        lv.setOnScrollListener(new EndlessScrollListener());
    }

    private void refreshWithSearch() {
        String terms;
        try {
            terms = encode(searchTerms, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG,"UTF-8 not supported?",e);
            return;
        }
        getActivity().setProgressBarIndeterminateVisibility(true);
        final int nextPage = pagesLoaded;
        service.search(settings.getUsername(),settings.getApiKey(),
                terms,countPP,nextPage,
                new Callback<SearchResult>() {

                    @Override
                    public void success(SearchResult searchResult, Response response) {
                        Log.w(TAG, "on success search :" + searchResult.result_count);
                        if(searchResult.result_count>0) {
                            bmarks.addAll(searchResult.search_results);
                            adapter.notifyDataSetChanged();
                            pagesLoaded++;
                        }
                        getActivity().setProgressBarIndeterminateVisibility(false);
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        getActivity().setProgressBarIndeterminateVisibility(false);
                        errorHandler.handleError(error);
                    }
                });
    }

    private void loadMoreData() {
        switch(state) {
            case ALL : refreshWithNewestGlobal(); break;
        }
    }

}