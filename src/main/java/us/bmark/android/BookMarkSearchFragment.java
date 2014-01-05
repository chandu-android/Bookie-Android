package us.bmark.android;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import us.bmark.bookieclient.Bookmark;

import static us.bmark.android.utils.Utils.isBlank;

public class BookMarkSearchFragment extends ListFragment {

    View view;
    private List<Bookmark> bmarks =
            new ArrayList<Bookmark>();
    private BookmarkArrayAdapter adapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = (View) getActivity().findViewById(R.layout.book_mark_search_layout);
        bmarks = ((BookMarkMainActivity) getActivity()).getAdapter();
        if (bmarks != null) {
            adapter = new BookmarkArrayAdapter(getActivity(),bmarks);
            setListAdapter(adapter);
        }
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

    public void reDrawList(List<Bookmark> bookMarks) {
        bmarks = bookMarks;
        adapter.notifyDataSetChanged();
    }
}
