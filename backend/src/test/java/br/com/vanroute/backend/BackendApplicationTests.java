package br.com.vanroute.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// sobe o backend inteiro (e roda as migrations) no banco vanroute_test, nunca no vanroute_db de desenvolvimento
@SpringBootTest
@ActiveProfiles({"local", "test"})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
