package it.ddp.tests;

import java.io.IOException;

import redis.embedded.RedisExecProvider;
import redis.embedded.RedisServer;
import redis.embedded.util.Architecture;
import redis.embedded.util.OS;




public class TestRedis00 {

	public TestRedis00() throws IOException {
		
		
		RedisExecProvider customProvider = RedisExecProvider.defaultProvider()
			.override(OS.WINDOWS, Architecture.x86, "C:\\Users\\esteton\\DEV\\WS-OXYGEN\\DDP\\lib\\redis\\redis-server-2.8.19-32.exe")
			.override(OS.WINDOWS, Architecture.x86_64, "C:\\Users\\esteton\\DEV\\WS-OXYGEN\\DDP\\lib\\redis\\redis-server-2.8.19.exe");
			//	  .override(OS.UNIX, "/path/to/unix/redis")
			//	  .override(OS.MAC_OS_X, Architecture.x86, "/path/to/macosx/redis")
			//	  .override(OS.MAC_OS_X, Architecture.x86_64, "/path/to/macosx/redis")
		
		
		RedisServer redisServer = RedisServer.builder()
			.redisExecProvider(customProvider)
			.port(6379)
			//.slaveOf("locahost", 6378)
			.setting("daemonize no")
			.setting("appendonly no")
			.setting("maxheap 512M")
			.build();
		
		redisServer.start();

		
		redisServer.stop();
	}

	public static void main(String[] args) throws IOException {
		new TestRedis00();
	}

}
