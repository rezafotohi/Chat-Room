
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Arrays;

import java.io.IOException;


public class ChatClient {
  public byte[] trim(byte[] bytes)
  {
      int i = bytes.length - 1;
      while (i >= 0 && bytes[i] == 0)
      {
          --i;
      }

      return Arrays.copyOf(bytes, i + 1);
  }
  public ChatClient(String username,String serverHost, int serverPort) 
                                                         throws IOException {

    Socket sock = new Socket(serverHost, serverPort);

    OutputStream out = sock.getOutputStream();

    new ReceiverThread(sock.getInputStream());

    byte[] key = InsecureSharedValue.getValue();
  BlockCipher bc = new BlockCipher(key);
    
    // Send out username to the server.
    // Add Carriage Return (CR) first; receiver will have to strip CR
    //   CR is needed because receiver takes input one line at a time;
    //   so we need to put the username onto its own line.
    //out.write((username+"\n").getBytes());
    //out.flush();
  
    // Read in what the user types, and send it to the server.
  byte[] text = new byte[256];
  int i = 0;
  for(byte b : (username).getBytes())
    text[i++] = b;
  byte[] outp = new byte[256];
  byte[] inp = text;
  for(int j = 0; j < Math.ceil(i / 8.0); j++) 
    bc.encrypt(inp, j * 8, outp, j * 8);
    for(int j = 0; j < Math.ceil(i / 8.0) * 8; j++) {
      out.write(outp[j]);
    }
    out.write('\n');
  out.flush();
      
  i = 0;
    for(;;i++){
      int c = System.in.read();
      if(c == -1)    break;
      // encrypt message and send
      if (c != '\n') text[i] = (byte)c;
      //out.write(c);
      
      if(c == '\n') {
        outp = new byte[256];
        //System.err.println(new String(text));
        inp = text;
        for(int j = 0; j < Math.ceil(i / 8.0); j++) 
          bc.encrypt(inp, j * 8, outp, j * 8);
          for(int j = 0; j < ((i / 8) + 1) *8; j++) {
            out.write(outp[j]);
          //System.out.print(j + ":" + outp[j] +",");
          }
          //System.out.println();
          out.write('\n');
        out.flush();  // Make sure server gets everything.
        int len = i;
        i = -1;
        //System.err.println(new String(outp));
        //byte[] tst = new byte[256];
        //for(int j = 0; j < Math.ceil(len / 8.0); j++) 
        //  bc.decrypt(outp, j * 8, tst, j * 8);
        //System.err.println(new String(tst));
      }
    }
    out.close();
  }

  public static void main(String[] argv){
    String username = "user1";
    if(argv.length == 1) 
      username = argv[0];
      
    String hostname = (argv.length<=1) ? "localhost" : argv[1];
    try{
      new ChatClient(username, hostname, ChatServer.portNum);
    }catch(IOException x){
      x.printStackTrace();
    }
  }

  class ReceiverThread extends Thread {
    // This is a thread that waits for bytes to arrive from the server.
    // When a whole line of text has arrived (or when the connection from 
    // the server is broken, it prints the line of incoming text.
    //
    // We put this in a separate thread so that the printing of incoming
    // text can proceed concurrently with the entry and sending of new
    // text.

    private InputStream in;

    ReceiverThread(InputStream inStream) {
      in = inStream;
      start();
    }

    public void run() {
      try{
  ByteArrayOutputStream baos;  // queues up stuff until carriage-return
  baos = new ByteArrayOutputStream();
  
  byte[] key = InsecureSharedValue.getValue();
  BlockCipher bc = new BlockCipher(key);
  
  
  for(;;){
    int c = in.read();
    if(c == -1){
      // connection from server was broken; output what we have
      spew(baos);
      break;
    }
    if (c != '\n')   baos.write(c);
    if (c == '\n')   spew(baos);  // got end of line; output what we have
  }
      }catch(IOException x){ }
    }

    private void spew(ByteArrayOutputStream baos) throws IOException {
      // Output the contents of baos; then reset (to empty) baos.
      //System.out.println("SPEW");
      byte[] message = baos.toByteArray();
      //System.out.println("LEN " + message.length + " " + new String(message));
      // decrypt message to show
      byte[] key = InsecureSharedValue.getValue();
      BlockCipher bc = new BlockCipher(key);
      byte[] outArr = new byte[256];
      for(int j = 0; j < Math.ceil((message.length - 1) / 8.0); j++)
        bc.decrypt(message, j * 8, outArr, j * 8);
      int len = 0;
      for(;;len++)
        if (outArr[len] == 0) break;
      byte[] strArr = new byte[len];
      for(int j = 0; j < len; j++)
        strArr[j] = outArr[j];
      System.out.println(new String(message));
      System.out.println(new String(strArr));
      baos.reset();
      //System.out.write(message);
    }
  }
}
