package org.scoula.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
		"jwt.secret.key=test_secret_key_for_jwt_in_testing_environment"
})
class BackendApplicationTests {

	// @Test
	// void contextLoads() {
	// }

}
