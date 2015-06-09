package socket.server;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.*;

// Socket server which take connections id and timeout in seconds.

class connection {
	private int connId;
	private volatile long timeout;
	private volatile long startTime;
	private volatile boolean processComplete;
	private Thread local = null;

	connection(int connId, long timeout) {
		this.connId = connId;
		this.timeout = timeout;
		this.startTime = 0;
		this.processComplete = false;
		this.local = Thread.currentThread();
	}

	protected int get_connid() {
		return this.connId;
	}

	protected long get_timeout(){
		return this.timeout;
	}

	protected long get_leftover() {
		if(this.startTime == 0)
			return -1;

		return(this.processComplete ? 0 : (this.timeout - (System.currentTimeMillis() - this.startTime)/1000));
	}

	protected Thread get_thread() {
		return this.local;
	}

	protected String start_process(long timeout) {
		this.startTime = System.currentTimeMillis();
		try{
			Thread.sleep(timeout*1000);
			//System.out.println("timeout complete");
			this.processComplete = true;
			return "ok";
		}
		catch(InterruptedException e) {
			//System.out.println("Thread is interrupted to stop.");
			return "killed";
		}
		finally{
			SocketServer.connections.remove(this.connId);
		}
	}
}

class worker implements Runnable {
	private Socket client = null;

	worker (Socket client){
		this.client = client;
	}



	@Override
	public void run() {
		BufferedReader inFromClient = null;
		DataOutputStream outToClient = null;
		try{
			inFromClient = new BufferedReader(new InputStreamReader(client.getInputStream()));
			outToClient = new DataOutputStream(client.getOutputStream());


			String request = inFromClient.readLine();
			//System.out.println(requestString);
			String headerLine = request;
			StringTokenizer tokenizer = new StringTokenizer(headerLine);
			String httpRequestType = tokenizer.nextToken(); //http request type
			String httpQuery = tokenizer.nextToken(); //http request string
			String status = null;        
			String code = null;
			JSONObject json = new JSONObject();

			if(httpRequestType.equals("GET")) {
				if (httpQuery.contains("/api/request")){

					String[] params = httpQuery.replaceAll("/api/request\\?", "").split("&");
					Map<String, String> map = new HashMap<String, String>();  
					for (String param : params)  
					{  
						String name = param.split("=")[0];  
						String value = param.split("=")[1];  
						map.put(name, value);  
					}
					if(map.containsKey("connId") && map.containsKey("timeout")){
						connection conn = new connection(Integer.parseInt(map.get("connId")), Long.parseLong(map.get("timeout")));
						if(SocketServer.connections.containsKey(conn.get_connid())){
							status = "Connection already exists";
						}
						else {
							SocketServer.connections.putIfAbsent(conn.get_connid(), conn);
							status = conn.start_process(conn.get_timeout());
							System.out.println("GET status - " + status);
						}  
						code = "200 Ok";
					}
					else {
						code = "400 Bad Request";
						status = "invalid parameters";
					}
					json.put("status", status);
					//System.out.println(json);
				}
				else if(httpQuery.equals("/api/serverStatus")) {
					System.out.println("hiah");
					Iterator<Map.Entry<Integer, connection>> iterator = SocketServer.connections.entrySet().iterator() ;
					while(iterator.hasNext()){
						Map.Entry<Integer, connection> connectionEntry = iterator.next();

						json.put(String.valueOf(connectionEntry.getKey()), String.valueOf((connectionEntry.getValue().get_leftover())));
					}
					code ="200 Ok";
					System.out.println(json);
				}
				else {
					code = "400 Bad Request";
				}
			}
			else if (httpRequestType.equals("PUT")){
				if(httpQuery.equals("/api/kill")){
					//System.out.println(" Put Kill Request");
					int length = 0;
					String line;
					while ((line = inFromClient.readLine()) != null) {
						if (line.equals("")) { // last line of request message, header is a blank line (\r\n\r\n)
							break; // quit while loop when last line of header is reached
						}

						// checking line if it has information about Content-Length
						// weather it has message body or not
						if (line.startsWith("Content-Length: ")) { // get the content-length
							int index = line.indexOf(':') + 1;
							String len = line.substring(index).trim();
							length = Integer.parseInt(len);
						} 
					} 
					StringBuffer body = new StringBuffer();
					if (length > 0) {
						int read;
						while ((read = inFromClient.read()) != -1) {
							body.append((char) read);
							if (body.length() == length)
								break;
						}
					}
					//System.out.println(body.toString());
					try{
						JSONObject jsonobject = (JSONObject) new JSONTokener(body.toString()).nextValue();
						int connid = jsonobject.getInt("connId");
						if(SocketServer.connections.containsKey(connid)){
							SocketServer.connections.get(connid).get_thread().interrupt();
							status = "ok";
						}
						else {
							status = "invalid connection Id : " + connid ;
						}
					}
					catch(JSONException e){
						status = "invalid parameters";
					}

				}
				json.put("status", status);
				code = "200 Ok";
				//System.out.println(json.toString());
			}
			else {
				code = "400 Bad Request";
			}
			outToClient.writeBytes("HTTP/1.1 " + code + "\r\n");
			outToClient.writeBytes("Content-Type: text/json\r\n");
			outToClient.writeBytes("Content-Length: " + json.toString().getBytes("utf8").length + "\r\n");
			outToClient.writeBytes("\r\n");
			outToClient.writeBytes(json.toString());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally{
			try{
				outToClient.close();
				inFromClient.close();
				client.close();
			}
			catch(IOException e){
				e.printStackTrace();
			}
		}

	}
}


class SocketServer implements Runnable {
	private boolean isServerStopped = false;
	private int port;
	private ServerSocket server = null;
	protected static ConcurrentHashMap<Integer, connection> connections = null;
	protected ExecutorService threadPool = Executors.newFixedThreadPool(20); //Threadpool of 20 threads. We can increase this number.

	SocketServer(int port) {
		this.port = port;
	}

	private synchronized boolean isServerStopped() {
		return this.isServerStopped;
	}

	protected synchronized void stopServer() {
		this.isServerStopped = true;
		try{
			this.server.close();
		}
		catch (IOException e){
			throw new RuntimeException("Error while stopping Server.", e);
		}
	}

	private void openServerSocket() {
		try {
			this.server = new ServerSocket(this.port);
		} catch (IOException e) {
			throw new RuntimeException("Error while opening port - " + this.port, e);
		}
	}


	@Override
	public void run() {
		this.openServerSocket();
		connections = new ConcurrentHashMap<Integer, connection>();
		while(!this.isServerStopped()) {
			Socket client = null;
			try{
				System.out.println("Server is Running");
				client = this.server.accept();
			}
			catch (IOException e) {
				if(this.isServerStopped()){
					System.out.println("Server is stopped.");
					break;
				}
				throw new RuntimeException("Error while accepting client connection", e);
			}
			this.threadPool.execute( new worker(client));
		}
		this.threadPool.shutdown();
		System.out.println("Server Stopped.") ;
	}

}


public class HttpSocketServer {

	public static void main(String[] args) {

		SocketServer server = new SocketServer(9000);
		new Thread(server).start();
		Scanner scanner = new Scanner(System.in);
		while(true){
			System.out.println("Type 'STOP' to stop the server.");
			String stop = scanner.next();
			
			if(stop.equals("STOP")){
				System.out.println("Stopping Server");
				server.stopServer(); //Here I am serving the request server has got before shutdown
				//We can kill all the threads running before server shutdown as well by iterating through the Hashmap and interrupting the threads.
				break;
			}
			else {
				System.out.println("Invalid Input.");
			}
		}
		scanner.close();
	}
}



