

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PushbackInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import java.io.IOException;


public class ChatServer {
  public static final int portNum = Config.getAsInt("ServerPortNum");

  // activeSenders is the list of clients that are currently active.
  private Set activeSenders = Collections.synchronizedSet(new HashSet());

  public ChatServer() {
    // This constructor never returns, unless there is an error.
    try{
      ServerSocket ss;
      ss = new ServerSocket(portNum);
      for(;;){
  // wait for a new client to connect, then hook it up properly
  Socket sock = ss.accept();
  InputStream  in  = sock.getInputStream();
  OutputStream out = sock.getOutputStream();
  System.err.println("Got connection");
  SenderThread sender = new SenderThread(out);
  new ReceiverThread(in, sender);
      }
    }catch(IOException x){
      x.printStackTrace();
    }
  }

  public static void main(String[] argv){
    new ChatServer();
  }

  class SenderThread extends Thread {
    // forwards messages to a client
    // messages are queued when somebody calls queueForSending
    // we take them from the queue and send them along

    private OutputStream out;
    private Queue        queue;

    SenderThread(OutputStream outStream) {
      out = outStream;
      queue = new Queue();
      activeSenders.add(this);
      start();
    }

    public void queueForSending(byte[] message){
      // Queue a message, to be sent as soon as possible.
      // We queue messages, rather than sending them immediately, because 
      //    sending immediately would cause us to block, if the client
      //    had fallen behind in processing his incoming messages.  If we
      //    blocked, the processing of incoming messages would be frozen,
      //    which would be Very Bad.  By queueing messages, we can ensure that
      //    the processing of incoming messages never stalls, no matter how
      //    badly clients behave.

      queue.put(message);
    }

    public void run() {
      // suck messages out of the queue and send them out
      try{
  for(;;){
    Object o = queue.get();
    byte[] barr = (byte[])o;
    out.write(barr);
    out.flush();
  }
      }catch(IOException x){
  // unexpected exception -- stop relaying messages
  x.printStackTrace();
  try{
    out.close();
  }catch(IOException x2){}
      }
      activeSenders.remove(this);
    }
  }

  class ReceiverThread extends Thread {
    // receives messages from a client, and forwards them to everybody else
    public byte[] trim(byte[] bytes)
    {
        int i = bytes.length - 1;
        while (i >= 0 && bytes[i] == 0)
        {
            --i;
        }

        return Arrays.copyOf(bytes, i + 1);
    }
    private InputStream  in;
    private SenderThread me;
    private String       userName;
    private byte[]       userNameBytes = null;

    ReceiverThread(InputStream inStream, SenderThread mySenderThread) {
      in = inStream;
      me = mySenderThread;
      start();
    }

    public void run() {
    // get first line, which is the client's username
    ByteArrayOutputStream baos = getOneLine(false);
    byte[] baosbuf = baos.toByteArray();
    byte[] key = InsecureSharedValue.getValue();
      BlockCipher bc = new BlockCipher(key);
      byte[] outArr = new byte[256];
      for(int j = 0; j < Math.ceil((baosbuf.length - 1) / 8.0); j++) 
        bc.decrypt(baosbuf, j * 8, outArr, j * 8);
      byte[] trimed = trim(outArr);
    String name = new String(trimed);
    //name = name.substring(0, name.length()-1); // trim trailing carriage return
    userName = name;
    name = "[" + name + "] ";
    userNameBytes = name.getBytes();
  
    //System.err.println(new String(userNameBytes));
    
    // get subsequent lines, and sent them to the other clients
    for(;;){
        baos = getOneLine(true);
        sendToOthers(baos);
    }
    }

    private ByteArrayOutputStream getOneLine(boolean prependUserName) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    // read in a message, terminated by carriage-return
    // buffer the message in baos, until we see EOF or carriage-return
    // return the message we got
    // if prependUserName is true, then prepend the username of the
          //    sender to the message before we return it
    try{
      byte[] ct = new byte[256];
        //if(prependUserName)    baos.write(userNameBytes);
        int c;
        int i = 0;
        do{
        c = in.read();
        if(c == -1){
            // got EOF -- return what we have, then quit
            return(baos);
        }
        
        if (!prependUserName) baos.write(c);
        if (c != '\n'){ 
          ct[i++] = (byte)c;
          //System.out.print(i + ":" + (byte)c +",");
        }
        }while(c != '\n');

          //System.out.println();
        // return what we have -- note: this includes a final CR
        if (prependUserName) {
          byte[] key = InsecureSharedValue.getValue();
          BlockCipher bc = new BlockCipher(key);
          byte[] outArr = new byte[256];
          for(int j = 0; j < Math.ceil(i / 8.0); j++) 
            bc.decrypt(ct, j * 8, outArr, j * 8);
          byte[] trimed = trim(outArr);
          int len8 = (int) (Math.ceil((userNameBytes.length + trimed.length)/8.0) * 8); 
          byte[] msg = new byte[len8];
          System.arraycopy(userNameBytes, 0, msg, 0, userNameBytes.length);
          System.arraycopy(trimed, 0, msg, userNameBytes.length, trimed.length);
          //System.out.println(new String(msg));
          byte[] buf = new byte[512];
          for(int j = 0; j < len8 / 8; j++)
            bc.encrypt(msg, j * 8, buf, j * 8);
          byte[] tmp = trim(buf);
          baos.write(tmp);
          
          baos.write('\n');
        }
        return(baos);
    }catch(IOException x){
        // return what we have, then quit
        return(baos);
    }
    }

    // stArr is a dummy variable, used to make toArray happy below
    private final SenderThread[] stArr = new SenderThread[1];

    private void sendToOthers(ByteArrayOutputStream baos) {
      // extract the contents of baos, and queue them for sending to all
      //    other clients (but not to ourself); 
      // also, reset baos so it is empty and can be reused

      byte[] message = baos.toByteArray();
      baos.reset();

      SenderThread[] guys = (SenderThread[])(activeSenders.toArray(stArr));
      for(int i=0; i<guys.length; ++i){
    SenderThread st = guys[i];
    if(st != me)  st.queueForSending(message);
      }
    }
  }
}
