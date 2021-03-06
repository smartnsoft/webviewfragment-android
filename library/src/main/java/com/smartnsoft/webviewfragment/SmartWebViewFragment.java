package com.smartnsoft.webviewfragment;

import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.smartnsoft.droid4me.LifeCycle.BusinessObjectsRetrievalAsynchronousPolicy;
import com.smartnsoft.droid4me.support.v4.app.SmartFragment;

/**
 * @author Ludovic Roland
 * @since 2016.06.03
 */
@BusinessObjectsRetrievalAsynchronousPolicy
public class SmartWebViewFragment<AggregateClass>
    extends SmartFragment<AggregateClass>
    implements OnClickListener
{

  public static final String PAGE_URL_EXTRA = "pageUrlExtra";

  public static final String SCREEN_TITLE_EXTRA = "screenTitleExtra";

  public static final String DEFAULT_ERROR_MESSAGE_EXTRA = "defaultErrorMessageExtra";

  public static final String ERROR_MESSAGES_TABLE_EXTRA = "errorMessagesTableExtra";

  //Views
  protected View loadingErrorAndRetry;

  protected View errorAndRetry;

  protected WebView webView;

  protected Button retry;

  protected TextView errorTextView;

  //webview state
  protected boolean webViewRestored = false;

  protected boolean errorWhenLoadingPage = false;

  protected String url;

  //network state
  protected boolean hasConnectivity = true;

  protected final BroadcastReceiver broadcastReceiver = new BroadcastReceiver()
  {
    @Override
    public void onReceive(Context context, Intent intent)
    {
      hasConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false) == false;
    }
  };

  protected NetworkCallback networkCallback;

  protected Map<String, Boolean> networkStatus = new HashMap<>();

  protected Map<Integer, String> errorMessagesTable = null;

  protected String defaultErrorMessage = null;

  protected int errorWhileLoading = -100;

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    final NetworkInfo activeNetworkInfo = getActiveNetworkInfo();
    if (activeNetworkInfo == null || activeNetworkInfo.isConnected() == false)
    {
      hasConnectivity = false;
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
    {
      registerBroadcastListenerOnCreate();
    }
    else
    {
      registerBroadcastListenerOnCreateLollipopAndAbove();
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
  {
    final View rootView = inflater.inflate(R.layout.web_view, container, false);

    setHasOptionsMenu(true);

    webView = rootView.findViewById(R.id.webview);
    loadingErrorAndRetry = rootView.findViewById(R.id.loadingErrorAndRetry);
    errorAndRetry = rootView.findViewById(R.id.errorAndRetry);
    retry = rootView.findViewById(R.id.retry);
    errorTextView = rootView.findViewById(R.id.errorText);

    retry.setOnClickListener(this);

    // Cookies management
    CookieManager.setAcceptFileSchemeCookies(true);
    CookieManager.getInstance().setAcceptCookie(true);

    if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP)
    {
      CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
      CookieManager.getInstance().flush();
    }

    webView.getSettings().setJavaScriptEnabled(true);
    webView.getSettings().setSupportMultipleWindows(true);

    if (VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN)
    {
      webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
      webView.getSettings().setAllowFileAccessFromFileURLs(true);
    }

    // Cache management
    webView.getSettings().setAppCachePath(getActivity().getApplicationContext().getCacheDir().getAbsolutePath());
    webView.getSettings().setAllowFileAccess(true);
    webView.getSettings().setAppCacheEnabled(true);
    webView.getSettings().setCacheMode(WebSettings.LOAD_DEFAULT);

    if (savedInstanceState != null)
    {
      webView.restoreState(savedInstanceState);
      webViewRestored = true;
    }
    else
    {
      webViewRestored = false;
    }

    return rootView;
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater)
  {
    super.onCreateOptionsMenu(menu, menuInflater);

    {
      final MenuItem menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.WebView_back)
          .setIcon(webView.canGoBack() == true ? R.drawable.ic_web_view_bar_previous_default : R.drawable.ic_web_view_bar_previous_disabled)
          .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener()
          {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem)
            {
              if (webView.canGoBack() == true)
              {
                webView.goBack();
              }
              return true;
            }
          });

      menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    {
      final MenuItem menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.WebView_refresh)
          .setIcon(R.drawable.ic_bar_refresh)
          .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener()
          {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem)
            {
              webView.reload();
              return true;
            }
          });

      menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    {
      final MenuItem menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.WebView_forward)
          .setIcon(webView.canGoForward() == true ? R.drawable.ic_web_view_bar_next_default : R.drawable.ic_web_view_bar_next_disabled)
          .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener()
          {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem)
            {
              if (webView.canGoForward() == true)
              {
                webView.goForward();
              }
              return true;
            }
          });

      menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    {
      final MenuItem menuItem = menu.add(Menu.NONE, Menu.NONE, Menu.NONE, R.string.WebView_openBrowser)
          .setIcon(R.drawable.ic_bar_open_browser)
          .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener()
          {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem)
            {
              final String actualUrl = webView.getUrl() != null ? webView.getUrl() : url;
              try
              {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(actualUrl)));
              }
              catch (Exception exception)
              {
                if (log.isWarnEnabled())
                {
                  log.warn("Could not open the native browser application for displaying the Internet page with URL '" + url + "'", exception);
                }
              }
              return true;
            }
          });

      menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

  }

  @Override
  public void onRetrieveDisplayObjects()
  {

  }

  @Override
  public void onRetrieveBusinessObjects()
      throws BusinessObjectUnavailableException
  {
    url = getActivity().getIntent().getStringExtra(SmartWebViewFragment.PAGE_URL_EXTRA);
    defaultErrorMessage = getActivity().getIntent().getStringExtra(SmartWebViewFragment.DEFAULT_ERROR_MESSAGE_EXTRA);

    if (getActivity().getIntent().getSerializableExtra(SmartWebViewFragment.ERROR_MESSAGES_TABLE_EXTRA) instanceof Map)
    {
      errorMessagesTable = (Map<Integer, String>) getActivity().getIntent().getSerializableExtra(SmartWebViewFragment.ERROR_MESSAGES_TABLE_EXTRA);
    }
  }

  @Override
  public void onFulfillDisplayObjects()
  {
    if (getActivity().getIntent().hasExtra(SmartWebViewFragment.SCREEN_TITLE_EXTRA) == false)
    {
      try
      {
        if (getActivity() instanceof AppCompatActivity)
        {
          ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(webView.getTitle());
        }
      }
      catch (Exception exception)
      {
        if (log.isWarnEnabled() == true)
        {
          log.warn("Cannot set the title", exception);
        }
      }
    }

    configureWebView();
    refreshMenu();
  }

  @Override
  public void onSynchronizeDisplayObjects()
  {

  }

  @Override
  public void onDestroy()
  {
    super.onDestroy();

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
    {
      unregisterBroadcastListenerOnDestroy();
    }
    else
    {
      unregisterBroadcastListenerLollipopAndAbove();
    }
  }

  @Override
  public void onSaveInstanceState(Bundle outState)
  {
    super.onSaveInstanceState(outState);
    webView.saveState(outState);
  }

  @Override
  public void onClick(View view)
  {
    if (view.equals(retry) == true)
    {
      refresh();
    }
  }

  protected void showLoadingScreen(boolean visible)
  {
    if (visible == true)
    {
      errorAndRetry.setVisibility(View.INVISIBLE);
      loadingErrorAndRetry.setVisibility(View.VISIBLE);
    }
    else
    {
      if (getActivity() != null && getActivity().isFinishing() == false)
      {
        final Animation animation = AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_out);
        animation.setAnimationListener(new AnimationListener()
        {
          @Override
          public void onAnimationStart(Animation animation)
          {
          }

          @Override
          public void onAnimationEnd(Animation animation)
          {
            loadingErrorAndRetry.setVisibility(View.INVISIBLE);
          }

          @Override
          public void onAnimationRepeat(Animation animation)
          {
          }

        });

        loadingErrorAndRetry.startAnimation(animation);
      }
      else
      {
        errorAndRetry.setVisibility(View.INVISIBLE);
        loadingErrorAndRetry.setVisibility(View.INVISIBLE);
      }
    }
  }

  protected void showErrorScreen(int errorCode)
  {
    handleErrorCode(errorCode);
    errorAndRetry.setVisibility(View.VISIBLE);
    loadingErrorAndRetry.setVisibility(View.VISIBLE);
  }

  protected void refresh()
  {
    if (hasConnectivity == true)
    {
      if (webView.getUrl() != null)
      {
        webView.reload();
      }
      else
      {
        webView.loadUrl(url);
      }
      errorWhenLoadingPage = false;
    }
    else
    {
      showErrorScreen(WebViewClient.ERROR_CONNECT);
    }
  }

  private void refreshMenu()
  {
    if (getActivity() != null && getActivity().isFinishing() == false)
    {
      getActivity().invalidateOptionsMenu();
    }
  }

  private void registerBroadcastListenerOnCreate()
  {
    final IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    getContext().registerReceiver(broadcastReceiver, intentFilter);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  private void registerBroadcastListenerOnCreateLollipopAndAbove()
  {
    if (networkCallback == null)
    {
      final NetworkRequest.Builder builder = new NetworkRequest.Builder();
      networkCallback = new ConnectivityManager.NetworkCallback()
      {
        @Override
        public void onAvailable(Network network)
        {
          networkStatus.put(network.toString(), true);
          hasConnectivity = networkStatus.containsValue(true);
        }

        @Override
        public void onLost(Network network)
        {
          networkStatus.remove(network.toString());
          hasConnectivity = networkStatus.containsValue(true);
        }
      };

      getConnectivityManager().registerNetworkCallback(builder.build(), networkCallback);
    }
  }

  private void unregisterBroadcastListenerOnDestroy()
  {
    getContext().unregisterReceiver(broadcastReceiver);
  }

  @TargetApi(VERSION_CODES.LOLLIPOP)
  private void unregisterBroadcastListenerLollipopAndAbove()
  {
    // We listen to the network connection potential issues: we do not want child activities to also register for the connectivity change events
    if (networkCallback != null)
    {
      getConnectivityManager().unregisterNetworkCallback(networkCallback);
      networkCallback = null;
    }
  }

  private NetworkInfo getActiveNetworkInfo()
  {
    return getConnectivityManager().getActiveNetworkInfo();
  }

  private ConnectivityManager getConnectivityManager()
  {
    return ((ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE));
  }

  private void configureWebView()
  {
    final WebViewClient webViewClient = new WebViewClient()
    {

      @Override
      public void onPageStarted(WebView view, String url, Bitmap favicon)
      {
        super.onPageStarted(view, url, favicon);
        showLoadingScreen(true);
        refreshMenu();
      }

      @Override
      public void onPageFinished(WebView view, String currentURL)
      {
        super.onPageFinished(view, currentURL);

        if (errorWhenLoadingPage == false && hasConnectivity == true)
        {
          showLoadingScreen(false);
        }
        else
        {
          showErrorScreen(errorWhileLoading);
        }

        refreshMenu();
      }

      @Override
      public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
      {
        super.onReceivedError(view, errorCode, description, failingUrl);
        errorWhenLoadingPage = true;
        errorWhileLoading = errorCode;
        refreshMenu();
      }

      @TargetApi(Build.VERSION_CODES.M)
      @Override
      public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError webSoWebResourceError)
      {
        super.onReceivedError(view, request, webSoWebResourceError);
        errorWhenLoadingPage = true;
        errorWhileLoading = webSoWebResourceError.getErrorCode();
        refreshMenu();
      }
    };

    final WebChromeClient webChromeClient = new WebChromeClient()
    {

      @Override
      public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg)
      {
        final WebView.HitTestResult result = view.getHitTestResult();
        final String data = result.getExtra();

        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(data)));

        return false;
      }

    };

    webView.setWebViewClient(webViewClient);
    webView.setWebChromeClient(webChromeClient);

    if (hasConnectivity == true)
    {
      if (webViewRestored == false)
      {
        webView.loadUrl(url);
      }
    }
    else
    {
      showErrorScreen(WebViewClient.ERROR_CONNECT);
    }
  }

  private void handleErrorCode(int errorCode)
  {
    if (errorMessagesTable != null && errorMessagesTable.containsKey(errorCode))
    {
      errorTextView.setText(errorMessagesTable.get(errorCode));
    }
    else if (defaultErrorMessage != null)
    {
      errorTextView.setText(defaultErrorMessage);
    }
  }

}
