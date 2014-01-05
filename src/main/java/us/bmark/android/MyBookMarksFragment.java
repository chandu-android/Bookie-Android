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
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import us.bmark.android.prefs.SharedPrefsBackedUserSettings;
import us.bmark.android.utils.IntentConstants;
import us.bmark.bookieclient.BookieService;
import us.bmark.bookieclient.BookieServiceUtils;
import us.bmark.bookieclient.Bookmark;
import us.bmark.bookieclient.BookmarkList;

import static us.bmark.android.utils.Utils.isBlank;

public class MyBookMarksFragment extends ListFragment{

    private static final String TAG = MyBookMarksFragment.class.getName();

    private int countPP;
    View view;
    private UserSettings settings;
    private BookmarkArrayAdapter adapter;
    private int pagesLoaded;
    private BookieService service;
    private List<Bookmark> bmarks =
            new ArrayList<Bookmark>();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = inflater.inflate(R.layout.mybook_marks_main, container, false);
        settings = new SharedPrefsBackedUserSettings(getActivity());
        countPP = getResources().getInteger(R.integer.default_number_of_bookmarks_to_get);
        adapter = new BookmarkArrayAdapter(getActivity(),bmarks);
        setUpService();
        setListAdapter(adapter);
        loadMoreData();
        return view;
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

    @Override
    public void onStart() {
        super.onStart();
        setUpListView();
    }

    private void setUpListView() {
        ListView lv = getListView();

        lv.setTextFilterEnabled(true);

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
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

        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
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

    private void setUpService() {
        String serverUrl = settings.getBaseUrl();
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setServer(serverUrl).build();
        restAdapter.setLogLevel(RestAdapter.LogLevel.FULL);
        service = restAdapter.create(BookieService.class);
    }

    private void refreshWithNewestUser() {
        int nextPage = pagesLoaded;
        getActivity().setProgressBarIndeterminateVisibility(true);
        service.recent(settings.getUsername(),
                settings.getApiKey(),
                countPP,
                nextPage,
                new ServiceCallback());
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
            //errorHandler.handleError(error);
        }
    }

    private void loadMoreData() {
        refreshWithNewestUser();
    }
}
