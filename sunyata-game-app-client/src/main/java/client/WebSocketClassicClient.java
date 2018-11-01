package client; /**
 * Created by leo on 17/4/14.
 */

import com.google.common.collect.ArrayListMultimap;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.context.ApplicationContext;
import org.sunyata.game.contract.Commands;
import org.sunyata.game.contract.protobuf.common.Common;
import org.sunyata.game.contract.protobuf.room.Room;
import org.sunyata.game.contract.protobuf.test.Test;

import javax.net.ssl.SSLException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * io.netty.handler.codec.http.websocketx.extension
 * Created by leo on 17/4/13.
 */
public final class WebSocketClassicClient {

    //CommandMessageManager commandMessageManager;
    static final String URL = System.getProperty("url", "ws://127.0.0.1:15002/websocket?token=tokenValuehahaha");
    //static final String URL = System.getProperty("url","ws://172.21.120.174:15002/websocket");
    private static AtomicLong serialCount = new AtomicLong();
    private static ApplicationContext applicationContext;

    public static Channel connect() throws SSLException, URISyntaxException {
        URI uri = new URI(URL);
        String scheme = uri.getScheme() == null ? "ws" : uri.getScheme();
        final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        final int port;
        if (uri.getPort() == -1) {
            if ("ws".equalsIgnoreCase(scheme)) {
                port = 80;
            } else if ("wss".equalsIgnoreCase(scheme)) {
                port = 443;
            } else {
                port = -1;
            }
        } else {
            port = uri.getPort();
        }

        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            System.err.println("Only WS(S) is supported.");
            return null;
        }

        final boolean ssl = "wss".equalsIgnoreCase(scheme);
        final SslContext sslCtx;
        if (ssl) {
            sslCtx = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE).build();
        } else {
            sslCtx = null;
        }

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // Connect with V13 (RFC 6455 aka HyBi-17). You can change it to V08 or V00.
            // If you change it to V00, ping is not supported and remember to change
            // HttpResponseDecoder to WebSocketHttpResponseDecoder in the pipeline.
            io.netty.handler.codec.http.cookie.DefaultCookie cookie = new io.netty.handler.codec.http.cookie
                    .DefaultCookie("cid", "tokenValud");
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setSecure(false);

            DefaultHttpHeaders entries = new DefaultHttpHeaders();
            entries.add(HttpHeaderNames.SET_COOKIE, io.netty.handler.codec.http.cookie.ServerCookieEncoder.LAX.encode
                    (cookie));
            //CommandMessageManager bean = SpringContextUtilForServer.getBean(CommandMessageManager.class);
            final WebSocketClientHandler handler =
                    new WebSocketClientHandler(applicationContext,
                            WebSocketClientHandshakerFactory.newHandshaker(
                                    uri, WebSocketVersion.V13, null, true, entries));

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            if (sslCtx != null) {
                                p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                            }
                            p.addLast(
                                    new HttpClientCodec(),
                                    new HttpObjectAggregator(8192),
                                    WebSocketClientCompressionHandler.INSTANCE,
                                    handler);
                        }
                    });

            Channel ch = b.connect(uri.getHost(), port).sync().channel();
            handler.handshakeFuture().sync();
            return ch;

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {

        }
        return null;
    }

    private final static ArrayListMultimap<String, Runnable> mapCallbacks = ArrayListMultimap.create();

    public static void start() throws IOException, URISyntaxException, InterruptedException {
        Channel ch = null;
        try {
            ch = connect();
            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String msg = console.readLine();
                if (msg == null) {
                    break;
                } else if ("bye".equals(msg.toLowerCase())) {
                    ch.writeAndFlush(new CloseWebSocketFrame());
                    ch.closeFuture().sync();
                    break;

                } else if ("connect".equals(msg.toLowerCase())) {
//                    ch.writeAndFlush(new CloseWebSocketFrame());
//                    ch.closeFuture().sync();
                    ch = connect();
                } else if ("ping".equals(msg.toLowerCase())) {
                    WebSocketFrame frame = new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[]{8, 1, 8, 1}));
                    ch.writeAndFlush(frame);

                } else if ("login".equals(msg.toLowerCase())) {
                    ByteBuf buffer = Unpooled.buffer();
                    byte[] bytes = "lcl".getBytes();
                    int length = bytes.length;
                    Common.LoginRequestMsg.Builder builder = Common.LoginRequestMsg.newBuilder();
                    builder.setType(1).setCode("password").setOpenId("13811465966").setLatitude("dsdf").setLongitude
                            ("sdfsdf");
                    byte[] loginBytes = builder.build().toByteArray();
                    buffer.writeInt(Integer.parseInt(Commands.login)).writeInt(2323232).writeFloat(1.0f).writeInt(-1)
                            .writeInt(loginBytes.length)
                            .writeBytes
                                    (loginBytes);
                    WebSocketFrame frame = new BinaryWebSocketFrame(buffer);
                    ch.writeAndFlush(frame);

                } else if ("login2".equals(msg.toLowerCase())) {
                    ByteBuf buffer = Unpooled.buffer();
                    byte[] bytes = "lcl".getBytes();
                    int length = bytes.length;
                    Common.LoginRequestMsg.Builder builder = Common.LoginRequestMsg.newBuilder();
                    builder.setType(1).setCode("password").setOpenId("13811465958").setLatitude("dsdf").setLongitude
                            ("sdfsdf");
                    byte[] loginBytes = builder.build().toByteArray();
                    buffer.writeInt(Integer.parseInt(Commands.login)).writeInt(2323232).writeFloat(1.0f).writeInt(-1)
                            .writeInt(loginBytes.length)
                            .writeBytes
                                    (loginBytes);
                    WebSocketFrame frame = new BinaryWebSocketFrame(buffer);
                    ch.writeAndFlush(frame);

                } else if ("test".equals(msg.toLowerCase())) {
                    ByteBuf buffer = Unpooled.buffer();
                    byte[] bytes = "lcl".getBytes();
                    int length = bytes.length;
                    Test.TestReq.Builder builder = Test.TestReq.newBuilder();
                    builder.setId(1111).setName("lcllclclcl");
                    byte[] loginBytes = builder.build().toByteArray();
                    buffer.writeInt(Integer.parseInt(Commands.testCommand)).writeInt(2323232).writeFloat(1.0f)
                            .writeInt(-1)
                            .writeInt(loginBytes.length)
                            .writeBytes
                                    (loginBytes);
                    WebSocketFrame frame = new BinaryWebSocketFrame(buffer);
                    ch.writeAndFlush(frame);
                } else if ("createroom".equals(msg.toLowerCase())) {
                    ByteBuf buffer = Unpooled.buffer();
                    Room.CreateRoomReq.Builder createRoomReq = Room.CreateRoomReq.newBuilder();
//                    KeyValueInfo userMax = getValue("userMax");
//                    KeyValueInfo chapterNums = getValue("chapterNums");
//                    KeyValueInfo createType = getValue("createType");;
                    Room.KeyValueInfo.Builder keyValueBuilder = Room.KeyValueInfo.newBuilder();
                    createRoomReq.addRuleIds(keyValueBuilder.setKey("userMax").setValue("4").build());
                    createRoomReq.addRuleIds(keyValueBuilder.setKey("chapterNums").setValue("8").build());
                    createRoomReq.addRuleIds(keyValueBuilder.setKey("createType").setValue("1").build());
                    createRoomReq.setGameType(31001);
                    byte[] bytes = createRoomReq.build().toByteArray();
                    int length = bytes.length;
                    buffer.writeInt(Integer.parseInt(Commands.createRoom)).writeInt(2323232).writeFloat(1.0f)
                            .writeInt(-1)
                            .writeInt(length)
                            .writeBytes
                                    (bytes);
                    WebSocketFrame frame = new BinaryWebSocketFrame(buffer);
                    ch.writeAndFlush(frame);


                } else if ("joinroom".equals(msg.toLowerCase())) {
                    ByteBuf buffer = Unpooled.buffer();
                    Room.JoinRoomReq.Builder createRoomReq = Room.JoinRoomReq.newBuilder();
//                    KeyValueInfo userMax = getValue("userMax");
//                    KeyValueInfo chapterNums = getValue("chapterNums");
//                    KeyValueInfo createType = getValue("createType");;
                    createRoomReq.setRoomCheckId("995555");
                    byte[] bytes = createRoomReq.build().toByteArray();
                    int length = bytes.length;
                    buffer.writeInt(Integer.parseInt(Commands.joinRoom)).writeInt(2323232).writeFloat(1.0f)
                            .writeInt(-1)
                            .writeInt(length)
                            .writeBytes
                                    (bytes);
                    WebSocketFrame frame = new BinaryWebSocketFrame(buffer);
                    ch.writeAndFlush(frame);
                } else if ("joingame".equals(msg.toLowerCase())) {
                    ByteBuf buffer = Unpooled.buffer();
                    Room.JoinGameReq.Builder createRoomReq = Room.JoinGameReq.newBuilder();
                    byte[] bytes = createRoomReq.build().toByteArray();
                    int length = bytes.length;
                    buffer.writeInt(Integer.parseInt(Commands.joinGame)).writeInt(2323232).writeFloat(1.0f)
                            .writeInt(-1)
                            .writeInt(length)
                            .writeBytes
                                    (bytes);
                    WebSocketFrame frame = new BinaryWebSocketFrame(buffer);
                    ch.writeAndFlush(frame);
                } else {
//                    LoginReq.LoginRequest login = LoginReq.LoginRequest.getDefaultInstance().toBuilder().setPassword
//                            ("password").setUserName(msg).build();
//                    ByteBuf buffer = Unpooled.buffer();
//                    byte[] bytes = login.toByteArray();
//                    buffer.writeInt(1111).writeLong(2323232).writeFloat(1.0f).writeInt(bytes.length).writeBytes
// (bytes);
//                    WebSocketFrame frame = new BinaryWebSocketFrame(buffer);
//                    ch.writeAndFlush(frame);
                }
            }
        } finally

        {
            //group.shutdownGracefully();
        }
    }

    static Thread t = null;

    public static void main(String[] args) throws Exception {
        t = new Thread(() -> {
            try {
                start();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });
        t.run();
        ;

    }


    public static void setApplicationContext(ApplicationContext applicationContext) {
        WebSocketClassicClient.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}