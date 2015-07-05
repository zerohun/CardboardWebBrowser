package com.entireangle.zerohoon.cardboardwebbrowser;// Fixed values

import android.content.Context;
import android.graphics.Canvas;
import android.view.Surface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

class CustomWebView extends WebView {

    public static Surface surface = null;
    public static final int TEXTURE_WIDTH        = ( 3000 );
    public static final int TEXTURE_HEIGHT       = ( 3000 );
    // Variables

    public CustomWebView(Context context) {
        super(context); // Call WebView's constructor
        setWebViewClient(new WebViewClient());
      //  setLayoutParams(new ViewGroup.LayoutParams(TEXTURE_WIDTH, TEXTURE_HEIGHT));
    }


    @Override
    protected void onDraw( Canvas canvas ) {
        if ( surface != null) {
            // Requires a try/catch for .lockCanvas( null )
            try {
                final Canvas surfaceCanvas = surface.lockCanvas( null ); // Android canvas from surface
                super.onDraw(surfaceCanvas); // Call the WebView onDraw targetting the canvas
                    surface.unlockCanvasAndPost( surfaceCanvas ); // We're done with the canvas!
            } catch ( Surface.OutOfResourcesException excp ) {
                excp.printStackTrace();
            }
        }
        // super.onDraw( canvas ); // <- Uncomment this if you want to show the original view
    }

}
