package us.bmark.android;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.widget.EditText;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import us.bmark.android.prefs.SettingsActivity;
import us.bmark.android.prefs.SharedPrefsBackedUserSettings;
import us.bmark.bookieclient.BookieService;
import us.bmark.bookieclient.Bookmark;
import us.bmark.bookieclient.SearchResult;

import static java.net.URLEncoder.encode;

public class BookMarkMainActivity extends FragmentActivity implements ActionBar.TabListener {

    static final int NUM_ITEMS = 3;
    static final String TAG = AllBookMarksListFragment.class.getName();
    private String searchTerms;
    private int pagesLoaded;
    private int countPP;
    private UserSettings settings;
    private BookieService service;
    private ActionBar actionBar;
    private List<Bookmark> bmarks =
            new ArrayList<Bookmark>();
    private String[] tabs = { "All", "Mine", "Search" };


    MainActivityPagerAdapter pagerAdapter;
    ViewPager viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        getActionBar().setDisplayHomeAsUpEnabled(false);   // Hides the '<' button in the ActionBar
        getActionBar().setHomeButtonEnabled(true);         // Enables the 'B' icon to be tappable on the list Activity
        setContentView(R.layout.bookie_main_container_layout);
        settings = new SharedPrefsBackedUserSettings(this);
        countPP = getResources().getInteger(R.integer.default_number_of_bookmarks_to_get);
        setUpService();
        pagerAdapter = new MainActivityPagerAdapter(getSupportFragmentManager());
        viewPager = (ViewPager)findViewById(R.id.pager);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageSelected(int position) {
                actionBar.setSelectedNavigationItem(position);
            }

            @Override
            public void onPageScrolled(int arg0, float arg1, int arg2) {
            }

            @Override
            public void onPageScrollStateChanged(int arg0) {
            }
        });

        actionBar = getActionBar();
        actionBar.setHomeButtonEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        // Adding Tabs
        for (String tab_name : tabs) {
            actionBar.addTab(actionBar.newTab().setText(tab_name)
                    .setTabListener(this));
        }

    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        viewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {

    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {

    }

//    private class BookmarkArrayAdapter extends ArrayAdapter<Bookmark> {
//
//        private static final int ROW_VIEW_ID = R.layout.allbookmarks_list_item;
//
//        BookmarkArrayAdapter(Context context, List<Bookmark> objects) {
//            super(context, ROW_VIEW_ID, objects);
//        }
//
//        @Override
//        public View getView(int position, View convertView, ViewGroup parent) {
//
//            View row = convertView;
//            Bookmark bmark = this.getItem(position);
//
//            if (row == null) {
//                LayoutInflater inflater = ((Activity) this.getContext()).getLayoutInflater();
//                row = inflater.inflate(ROW_VIEW_ID, parent, false);
//            }
//
//            TextView textView = (TextView) row.findViewById(R.id.bookmarkListRowTextView);
//            final String description = isBlank(bmark.description) ? bmark.url : bmark.description;
//            textView.setText(description);
//
//            return row;
//        }
//    }

    private static class MainActivityPagerAdapter extends FragmentPagerAdapter{

        public MainActivityPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int index) {
            switch (index) {
                case 0:
                    return new AllBookMarksListFragment();
                case 1:
                    return new MyBookMarksFragment();
                case 2:
                    return new BookMarkSearchFragment();
            }
            return null;
        }

        @Override
        public int getCount() {
            return NUM_ITEMS;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_activity_actions, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch( item.getItemId() ) {
//            case android.R.id.home:
//            case R.id.action_everyones_recent:
//                Log.v(TAG, "global button clicked");
//                //flipState(State.ALL);
//                return true;
//            case R.id.action_recent:
//                Log.v(TAG, "user button clicked");
//                //flipState(State.MINE);
//                return true;
            case R.id.action_settings:
                Intent settingsIntent =
                        new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            case R.id.action_search:
                displaySearchDialog();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void refreshWithSearch() {
        String terms;
        try {
            terms = encode(searchTerms, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "UTF-8 not supported?", e);
            return;
        }
        setProgressBarIndeterminateVisibility(true);
        final int nextPage = pagesLoaded;
        service.search(settings.getUsername(),settings.getApiKey(),
                terms,countPP,nextPage,
                new Callback<SearchResult>() {

                    @Override
                    public void success(SearchResult searchResult, Response response) {
                        Log.w(TAG, "on success search :" + searchResult.result_count);
                        if(searchResult.result_count>0) {
                            bmarks.addAll(searchResult.search_results);
                            // adapter = new BookmarkArrayAdapter(BookMarkMainActivity.this,bmarks);
                            //adapter.notifyDataSetChanged();
                            viewPager.setCurrentItem(2);
                            BookMarkSearchFragment searchFragment =   (BookMarkSearchFragment) getSupportFragmentManager().findFragmentByTag("android:switcher:" + R.id.pager + ":" + viewPager.getCurrentItem());
                            if (searchFragment != null) {
                                searchFragment.reDrawList(bmarks);
                            }
                            pagesLoaded++;
                        }
                        setProgressBarIndeterminateVisibility(false);
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        setProgressBarIndeterminateVisibility(false);
                        //errorHandler.handleError(error);
                    }
                });
    }

    public List<Bookmark> getAdapter() {
        return  bmarks;
    }

    private void setUpService() {
        String serverUrl = settings.getBaseUrl();
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setServer(serverUrl).build();
        restAdapter.setLogLevel(RestAdapter.LogLevel.FULL);
        service = restAdapter.create(BookieService.class);
    }

    private void displaySearchDialog() {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);

        alert.setTitle(R.string.search_dialog_title);
        alert.setMessage(R.string.search_dialog_message);

        final EditText input = new EditText(this);
        alert.setView(input);

        alert.setPositiveButton(R.string.search_dialog_positive_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                searchTerms = input.getText().toString();
                refreshWithSearch();
            }
        });

        alert.setNegativeButton(R.string.search_dialog_cancel_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int whichButton) {
                // Do nothing
            }
        });

        alert.show();
    }
}
