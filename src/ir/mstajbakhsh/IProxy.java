package ir.mstajbakhsh;

import java.net.Socket;

public interface IProxy {
    void prepareAndStartTunnelThreads(Socket innerSocket, Socket hiddenSocket);
    //TODO change to Address
    void startServer();
    void createInnerHandler();
}
