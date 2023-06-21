/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
See License.txt in the project root for license information.
*/

package microsoft.aspnet.signalr.client.transport;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import com.google.gson.Gson;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.InvalidDataException;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.util.Charsetfunctions;

import microsoft.aspnet.signalr.client.ConnectionBase;
import microsoft.aspnet.signalr.client.LogLevel;
import microsoft.aspnet.signalr.client.Logger;
import microsoft.aspnet.signalr.client.SignalRFuture;
import microsoft.aspnet.signalr.client.UpdateableCancellableFuture;
import microsoft.aspnet.signalr.client.http.HttpConnection;

/**
 * Implements the WebsocketTransport for the Java SignalR library
 * Created by stas on 07/07/14.
 */
public class WebsocketTransport extends HttpClientTransport {

    private String mPrefix;
    private static final Gson gson = new Gson();
    WebSocketClient mWebSocketClient;
    private Logger logger;
    private UpdateableCancellableFuture<Void> mConnectionFuture;

    public WebsocketTransport(Logger logger) {
        super(logger);
        this.logger = logger;
    }

    public WebsocketTransport(Logger logger, HttpConnection httpConnection) {
        super(logger, httpConnection);
    }

    @Override
    public String getName() {
        return "webSockets";
    }

    @Override
    public boolean supportKeepAlive() {
        return true;
    }

    @Override
    public SignalRFuture<Void> start(ConnectionBase connection, ConnectionType connectionType, final DataResultCallback callback) {
        String url = connection.getUrl() + (connectionType == ConnectionType.InitialConnection ? "connect" : "reconnect")
                + TransportHelper.getReceiveQueryString(this, connection);;
        if(url.startsWith("https://"))
            url = url.replaceFirst("https://","wss://");
        else if(url.startsWith("http://"))
            url = url.replaceFirst("https://","ws://");
        mConnectionFuture = new UpdateableCancellableFuture<Void>(null);

        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            mConnectionFuture.triggerError(e);
            return mConnectionFuture;
        }

        mWebSocketClient = new WebSocketClient(uri, connection.getHeaders()) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                mConnectionFuture.setResult(null); logger.log("onOpen", LogLevel.Information);
            }

            @Override
            public void onMessage(String s) {
                callback.onData(s);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                mWebSocketClient.close();
                logger.log("onClose" + s, LogLevel.Information);
            }

            @Override
            public void onError(Exception e) {
                mWebSocketClient.close();
                logger.log("onError", LogLevel.Information);
            }

            // @Override
            // public void onFragment(Framedata frame) {
            //     try {
            //         String decodedString = Charsetfunctions.stringUtf8(frame.getPayloadData());

            //         if(decodedString.equals("]}")){
            //             return;
            //         }

            //         if(decodedString.endsWith(":[") || null == mPrefix){
            //             mPrefix = decodedString;
            //             return;
            //         }

            //         String simpleConcatenate = mPrefix + decodedString;

            //         if(isJSONValid(simpleConcatenate)){
            //             onMessage(simpleConcatenate);
            //         }else{
            //             String extendedConcatenate = simpleConcatenate + "]}";
            //             if (isJSONValid(extendedConcatenate)) {
            //                 onMessage(extendedConcatenate);
            //             } else {
            //                 log("invalid json received:" + decodedString, LogLevel.Critical);
            //             }
            //         }
            //     } catch (InvalidDataException e) {
            //         e.printStackTrace();
            //     }
            // }
        };
        mWebSocketClient.connect();

        connection.closed(new Runnable() {
            @Override
            public void run() {
                mWebSocketClient.close();
            }
        });

        return mConnectionFuture;
    }

    @Override
    public SignalRFuture<Void> send(ConnectionBase connection, String data, DataResultCallback callback) {
        mWebSocketClient.send(data);
        return new UpdateableCancellableFuture<Void>(null);
    }

    private boolean isJSONValid(String test){
        try {
            gson.fromJson(test, Object.class);
            return true;
        } catch(com.google.gson.JsonSyntaxException ex) {
            return false;
        }
    }
}