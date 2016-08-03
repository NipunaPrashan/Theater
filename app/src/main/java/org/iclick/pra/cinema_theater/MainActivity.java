package org.iclick.pra.cinema_theater;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.Log;
import android.util.LruCache;
import android.view.View;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.api.GoogleApiClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private final String LOG_TAG = MainActivity.class.getSimpleName();
    private LruCache<String, Bitmap> mMemoryCache;
    private DiskLruCache mDiskLruCache;
    private final Object mDiskCacheLock = new Object();
    private static final int DISK_CACHE_SIZE = 1024 * 1024 * 10; // 10MB
    private static final String DISK_CACHE_SUBDIR = "cinema-theater";
    private Bitmap.CompressFormat mCompressFormat = Bitmap.CompressFormat.JPEG;
    private GoogleApiClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

//        final SwipeRefreshLayout swipeViw = (SwipeRefreshLayout) findViewById(R.id.swipe);
//        swipeViw.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
//
//            @Override
//            public void onRefresh() {
//                swipeViw.setRefreshing(true);
//                (new Handler()).postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        swipeViw.setRefreshing(false);
//                        updateMovieDetails();
//                    }
//                },10);
//            }
//        });

        final int maxMemorySize = (int) Runtime.getRuntime().maxMemory() / 1024;
        final int cacheSize = maxMemorySize / 8;

        mMemoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        // Initialize disk cache on background thread
        File cacheDir = getDiskCacheDir(this, DISK_CACHE_SUBDIR);
        new InitDiskCacheTask().execute(cacheDir);

        //update only network is enabled
        if (isNetworkConnected()) {
            updateMovieDetails();
        } else {
            offlineUpdate();
        }

        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    private void offlineUpdate() {
        Movie[] movie;
        movie = getMovieDetails();

        for (int i = 0; i < 5; i++) {
            String key = String.valueOf(i);

            Bitmap cachebitmap = getBitmapFromMemCache(key);
            Bitmap diskbitmap = getBitmapFromDisk(key);

            if (cachebitmap != null) {
                load(movie, cachebitmap, i, this);
            } else if (diskbitmap != null) {
                load(movie, diskbitmap, i, this);
            }
        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        return cm.getActiveNetworkInfo() != null;
    }

    @Override
    public void onStart() {
        super.onStart();
        client.connect();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://org.iclick.pra.cinema_theater/http/host/path")
        );
        AppIndex.AppIndexApi.start(client, viewAction);
    }

    @Override
    public void onStop() {
        super.onStop();
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "Main Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://org.iclick.pra.cinema_theater/http/host/path")
        );
        AppIndex.AppIndexApi.end(client, viewAction);
        client.disconnect();
    }

    class InitDiskCacheTask extends AsyncTask<File, Void, Void> {
        @Override
        protected Void doInBackground(File... params) {
            synchronized (mDiskCacheLock) {
                File cacheDir = params[0];
                try {
                    mDiskLruCache = DiskLruCache.open(cacheDir, 1, 1, DISK_CACHE_SIZE);
                } catch (IOException e) {
                    Log.e(LOG_TAG, e.toString());
                }
                boolean mDiskCacheStarting = false;
                mDiskCacheLock.notifyAll(); // Wake any waiting threads
            }
            return null;
        }
    }

    public void updateMovieDetails() {
        FilmRetriever imageTask = new FilmRetriever(this);
        imageTask.execute();
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Handling navigation item operations
     *
     * @param item selected menu item
     * @return value
     */
    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_recommended) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_trailer) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }


    public class FilmRetriever extends AsyncTask<Void, Void, Movie[]> {

        private final String LOG_TAG = FilmRetriever.class.getSimpleName();

        public Activity activity;

        public FilmRetriever(Activity a) {
            this.activity = a;
        }

        private Movie[] getMovieDataFromJson(String movieJsonStr)
                throws JSONException {

            final String RESULT = "results";
            final String POSTER_PATH = "poster_path";
            final String OVERVIEW = "overview";
            final String RELEASE_DATE = "release_date";
            final String ID = "id";
            final String TITLE = "original_title";
            final String LANGUAGE = "original_language";
            final String BACKDROP_PATH = "backdrop_path";
            final String POPULARITY = "popularity";
            final int noOfMovies = 10;


            JSONObject forecastJson = new JSONObject(movieJsonStr);
            JSONArray movieArray = forecastJson.getJSONArray(RESULT);

            Movie[] resultMovies = new Movie[noOfMovies];
            for (int i = 0; i < noOfMovies; i++) {
                JSONObject movie = movieArray.getJSONObject(i);

                Movie newMovie = new Movie();
                newMovie.setPOSTER_PATH(movie.getString(POSTER_PATH));
                newMovie.setOVERVIEW(movie.getString(OVERVIEW));
                newMovie.setRELEASE_DATE(movie.getString(RELEASE_DATE));
                newMovie.setID(movie.getString(ID));
                newMovie.setTITLE(movie.getString(TITLE));
                newMovie.setLANGUAGE(movie.getString(LANGUAGE));
                newMovie.setBACKDROP_PATH(movie.getString(BACKDROP_PATH));
                newMovie.setPOPULARITY(movie.getString(POPULARITY));

                resultMovies[i] = newMovie;
            }
            return resultMovies;
        }

        @Override
        protected Movie[] doInBackground(Void... params) {

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            String movieJsonStr = null;
            String format = "json";
            String language = "en";
            String APPID = "06227ed9cd894850233bdcc0ce688a02";

            try {
                final String FORECAST_URL = "http://api.themoviedb.org/3/movie/upcoming?";
                final String LANGUAGE = "language";
                final String APPID_PARAM = "api_key";

                Uri builtUri = Uri.parse(FORECAST_URL).buildUpon()
                        .appendQueryParameter(APPID_PARAM, APPID)
                        .appendQueryParameter(LANGUAGE, language).build();


                URL url = new URL(builtUri.toString());
                Log.v(LOG_TAG, "Built URL: " + builtUri.toString());

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    return null;
                }
                movieJsonStr = buffer.toString();

                Log.v(LOG_TAG, "Forecast JSON String: " + movieJsonStr);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }
            try {
                return getMovieDataFromJson(movieJsonStr);
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            return null;
        }


        @Override
        protected void onPostExecute(final Movie[] movie) {

            saveMovieDetail(movie);

            for (int i = 0; i < 5; i++) {
                String key = String.valueOf(i);

                Bitmap cachebitmap = getBitmapFromMemCache(key);
                Bitmap diskbitmap = getBitmapFromDisk(key);

                if (cachebitmap != null) {
                    load(movie, cachebitmap, i);
                } else if (diskbitmap != null) {
                    load(movie, diskbitmap, i);
                } else {
                    create(movie, i);
                }
                diskbitmap = null;
                cachebitmap = null;
            }
        }

        protected void load(Movie[] movie, Bitmap bitmap, int i) {
            LinearLayout recommendLayout = (LinearLayout) findViewById(R.id.linear);
//            LinearLayout newLayout = (LinearLayout) findViewById(R.id.linear2);

            final ImageView imageView = new ImageView(activity);
            imageView.setId(i);
            imageView.setPadding(2, 2, 2, 2);
            int width = 300;
            int height = 450;
            LinearLayout.LayoutParams parms = new LinearLayout.LayoutParams(width, height);
            imageView.setLayoutParams(parms);
            imageView.setImageBitmap(bitmap);
            recommendLayout.addView(imageView);

            final Movie theMovie = movie[i];
            imageView.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(activity, DetailActivity.class).putExtra("MyClass", theMovie);
                    startActivity(intent);
                }
            });


//            final ImageView imageView2 = new ImageView(activity);
//            imageView2.setId(i);
//            imageView2.setPadding(2, 2, 2, 2);
//
//            LinearLayout.LayoutParams parms2 = new LinearLayout.LayoutParams(width, height);
//            imageView2.setLayoutParams(parms2);
//            imageView2.setImageBitmap(bitmap);
//            newLayout.addView(imageView2);
//
//            final Movie theMovie2 = movie[i];
//            imageView2.setOnClickListener(new View.OnClickListener() {
//
//                @Override
//                public void onClick(View v) {
//                    Intent intent = new Intent(activity, DetailActivity.class).putExtra("MyClass", theMovie2);
//                    startActivity(intent);
//                }
//            });

        }

        protected void create(Movie[] movie, int i) {
            LinearLayout recommendLayout = (LinearLayout) findViewById(R.id.linear);
//            LinearLayout newLayout = (LinearLayout) findViewById(R.id.linear2);
            final String imageBaseUrl = "http://image.tmdb.org/t/p/w500";
            ;

            final ImageView imageView = new ImageView(activity);
            imageView.setId(i);
            imageView.setPadding(2, 2, 2, 2);
            int width = 300;
            int height = 450;
            LinearLayout.LayoutParams parms = new LinearLayout.LayoutParams(width, height);
            imageView.setLayoutParams(parms);
            imageView.setImageBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.tarzan));

            new DownloadImageTask(imageView).execute((imageBaseUrl + movie[i].getPOSTER_PATH()), String.valueOf(i));

            recommendLayout.addView(imageView);

            final Movie theMovie = movie[i];
            imageView.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(activity, DetailActivity.class).putExtra("MyClass", theMovie);
                    startActivity(intent);
                }
            });

//                final ImageView imageView2 = new ImageView(activity);
//                imageView2.setId(i);
//                imageView2.setPadding(2, 2, 2, 2);
//                LinearLayout.LayoutParams parms2 = new LinearLayout.LayoutParams(width, height);
//                imageView2.setLayoutParams(parms2);
//                new DownloadImageTask(imageView2)
//                        .execute((imageBaseUrl + movie[i].getPOSTER_PATH()),String.valueOf(i));
//
//                imageView2.setImageBitmap(BitmapFactory.decodeResource(
//                        getResources(), R.drawable.tarzan));
//                newLayout.addView(imageView2);
//                final Movie theMovie2 = movie[i];
//                imageView2.setOnClickListener(new View.OnClickListener() {
//
//                    @Override
//                    public void onClick(View v) {
//                        Intent intent = new Intent(activity, DetailActivity.class).putExtra("MyClass", theMovie2);
//                        startActivity(intent);
//                    }
//                });

        }

    }

    private void saveMovieDetail(Movie[] movie) {
        FileOutputStream outStream = null;
        try {
            File cacheDir = getDiskCacheDir(this, DISK_CACHE_SUBDIR);
            outStream = new FileOutputStream(cacheDir + "/MovieList.dat");
            ObjectOutputStream objectOutStream = null;
            objectOutStream = new ObjectOutputStream(outStream);

            objectOutStream.writeInt(movie.length); // Save size first
            for (Movie r : movie)
                objectOutStream.writeObject(r);

            objectOutStream.close();
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, e.toString());
        } catch (IOException e) {
            Log.e(LOG_TAG, e.toString());
        }
    }

    private Movie[] getMovieDetails() {
        File cacheDir = getDiskCacheDir(this, DISK_CACHE_SUBDIR);
        Movie[] movieList = new Movie[10];
        try {
            FileInputStream inStream = new FileInputStream(cacheDir + "/MovieList.dat");

            ObjectInputStream objectInStream = new ObjectInputStream(inStream);
            int count = objectInStream.readInt(); // Get the number of regions
            for (int c = 0; c < count; c++)
                movieList[c] = ((Movie) objectInStream.readObject());
            objectInStream.close();
        } catch (IOException e) {
            Log.e(LOG_TAG, e.toString());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return movieList;
    }

    public void addBitmapToCache(String key, Bitmap bitmap) {
        // Add to memory cache as before
        if (getBitmapFromMemCache(key) == null) {
            mMemoryCache.put(key, bitmap);
        }

        // Also add to disk cache
        synchronized (mDiskCacheLock) {
            try {
                if (mDiskLruCache != null && mDiskLruCache.get(key) == null) {
                    put(key, bitmap);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void put(String key, Bitmap data) {

        DiskLruCache.Editor editor = null;
        try {
            editor = mDiskLruCache.edit(key);
            if (editor == null) {
                return;
            }

            if (writeBitmapToFile(data, editor)) {
                mDiskLruCache.flush();
                editor.commit();

                if (BuildConfig.DEBUG) {
                    Log.v(LOG_TAG, "--------------------------------------------------------");
                    Log.d("cache_test_DISK_", "image put on disk cache " + key);
                }
            } else {
                editor.abort();
                if (BuildConfig.DEBUG) {
                    Log.d("cache_test_DISK_", "ERROR on: image put on disk cache " + key);
                }
            }
        } catch (IOException e) {
            if (BuildConfig.DEBUG) {
                Log.v(LOG_TAG, "******************************************************************");
                Log.d("cache_test_DISK_", "ERROR on: image put on disk cache " + key);
            }
            try {
                if (editor != null) {
                    editor.abort();
                }
            } catch (IOException ignored) {
            }
        }

    }

    private boolean writeBitmapToFile(Bitmap bitmap, DiskLruCache.Editor editor)
            throws IOException, FileNotFoundException {
        OutputStream out = null;
        try {
            out = new BufferedOutputStream(editor.newOutputStream(0), Util.IO_BUFFER_SIZE);
            int mCompressQuality = 70;
            return bitmap.compress(mCompressFormat, mCompressQuality, out);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    public Bitmap getBitmapFromMemCache(String key) {
        return mMemoryCache.get(key);
    }

    public Bitmap getBitmapFromDisk(String key) {

        Bitmap bitmap = null;
        DiskLruCache.Snapshot snapshot = null;
        File cacheDir = getDiskCacheDir(this, DISK_CACHE_SUBDIR);
        try {
            mDiskLruCache = DiskLruCache.open(cacheDir, 1, 1, DISK_CACHE_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            Log.v(LOG_TAG, "--------------------------------------------------------");
            snapshot = mDiskLruCache.get(key);
            Log.v(LOG_TAG, "--------------------------------------------------------");
            if (snapshot == null) {
                return null;
            }
            final InputStream in = snapshot.getInputStream(0);
            if (in != null) {
                final BufferedInputStream buffIn =
                        new BufferedInputStream(in, Util.IO_BUFFER_SIZE);
                bitmap = BitmapFactory.decodeStream(buffIn);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (snapshot != null) {
                snapshot.close();
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d("cache_test_DISK_", bitmap == null ? "" : "image read from disk " + key);
        }

        return bitmap;
    }

    private class DownloadImageTask extends AsyncTask<String, Void, Bitmap> {
        ImageView bmImage;

        public DownloadImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        protected Bitmap doInBackground(String... param) {
            String urldisplay = param[0];
            String key = param[1];
            Bitmap mIcon11 = null;
            try {
                InputStream in = new URL(urldisplay).openStream();
                mIcon11 = BitmapFactory.decodeStream(in);
                addBitmapToCache(key, mIcon11);
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return mIcon11;
        }

        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);
        }
    }


    private File getDiskCacheDir(Context context, String uniqueName) {

        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        final String cachePath =
                Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                        !Util.isExternalStorageRemovable() ?
                        Util.getExternalCacheDir(context).getPath() :
                        context.getCacheDir().getPath();

        return new File(cachePath + File.separator + uniqueName);
    }

    private void load(Movie[] movie, Bitmap bitmap, int i, final Activity activity) {
        LinearLayout recommendLayout = (LinearLayout) findViewById(R.id.linear);
//        LinearLayout newLayout = (LinearLayout) findViewById(R.id.linear2);

        final ImageView imageView = new ImageView(activity);
        imageView.setId(i);
        imageView.setPadding(2, 2, 2, 2);
        int width = 300;
        int height = 450;
        LinearLayout.LayoutParams parms = new LinearLayout.LayoutParams(width, height);
        imageView.setLayoutParams(parms);
        imageView.setImageBitmap(bitmap);
        recommendLayout.addView(imageView);

        final Movie theMovie = movie[i];
        imageView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent intent = new Intent(activity, DetailActivity.class).putExtra("MyClass", theMovie);
                startActivity(intent);
            }
        });


//            final ImageView imageView2 = new ImageView(activity);
//            imageView2.setId(i);
//            imageView2.setPadding(2, 2, 2, 2);
//
//            LinearLayout.LayoutParams parms2 = new LinearLayout.LayoutParams(width, height);
//            imageView2.setLayoutParams(parms2);
//            imageView2.setImageBitmap(bitmap);
//            newLayout.addView(imageView2);
//
//            final Movie theMovie2 = movie[i];
//            imageView2.setOnClickListener(new View.OnClickListener() {
//
//                @Override
//                public void onClick(View v) {
//                    Intent intent = new Intent(activity, DetailActivity.class).putExtra("MyClass", theMovie2);
//                    startActivity(intent);
//                }
//            });

    }
}
