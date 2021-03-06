

SocketServer listening on Port 9000.

Main class HttpSocketServer having main method - starts a server by creating a new thread which listens on port 9000. Server can be stopped by providing STOP to the main method.

class SocketServer is the controller class of the server having static ConcurrentHashMap of connection type(refer class connection) for tracking connections through GET request. It also has thread pool of capacity 20 threads(which can be changed). Every new request is added to it and the threads if available executes it or will execute it once they are free.  Here, we can also start a new thread for each request but taking scalability in mind, I have used thread pool as server can get thousands of request and it will be hard to manage thousands of threads. We can increase the capacity of the server's thread pool as per the traffic coming. Methods in this class are self explanatory.

class connection has all the parameters associated with the connection. It also has the process function for the GET requests and PUT requests.

class worker defines how a thread in the thread pool should behave and extract information to be processed by the connection. Here we are checking the type of request (GET or PUT), reading GET parameters, parsing the JSON and creating the response for the client. For GET api/request?connId=12&timeout=100, we create a connection object having connId = 12 and timeout = 100, add it to ConcurrentHashMap and starts processing for this connection which is putting the thread to sleep for 100 seconds and after process gets completed remove it from the ConcurrentHashMap. For GET api/serverStatus, we iterate the ConcurrentHashMap and provide all the connection's ids and their leftover time. For PUT api/kill with payload as {connId:12} we find the connection with connId 12 in ConcurrentHashMap and interrupt the process which will kill the connection and remove it from the ConcurrentHashMap.

Program has comments written along with it. One can easily understand it's working.
