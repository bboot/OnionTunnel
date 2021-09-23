package ir.mstajbakhsh;

import java.net.Socket;
import java.util.UUID;

public class OnionProxy implements IProxy {
    final String _id;
    String _Host;
    String _HiddenHost;
    int _Port;
    int _HiddenPort;
    ITunnelStatus eventHandler;

    public OnionProxy(String Host, int Port, ITunnelStatus eventHandler) {
        this._id = UUID.randomUUID().toString().substring(24);
        this._Host = Host;
        this._HiddenHost = Host;
        this._Port = Port;
        this._HiddenPort = Port;
        this.eventHandler = eventHandler;

        if (getType() == AddressHelper.AddressType.HiddenService) {
            System.out.println("CREATE INNER HANDLER!!!");
            createInnerHandler();
        }
    }

    @Override
    public void prepareAndStartTunnelThreads(Socket innerSocket, Socket hiddenSocket) {

    }

    @Override
    public void startServer() {

    }

    @Override
    public void createInnerHandler() {

    }

    public int getPort() {
        return _Port;
    }

    public String getHost() {
        return _Host;
    }

    public AddressHelper.AddressType getType() {
        return AddressHelper.getType(getHost());
    }

    @Override
    public String toString() {
        return "id:" + _id + " " + getHost() + ":" + getPort() + "<=>" + _HiddenHost + ":" + _HiddenPort;
    }

    public String getProxyType() {
        return "";
    }
}
