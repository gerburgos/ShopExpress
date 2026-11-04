package com.shopexpress.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Boot 4.1.2 no crea un administrador de transacciones para MongoDB (se verificó en el jar de
// spring-boot-data-mongodb: no trae ninguna autoconfiguración de transacciones). Sin él, las operaciones de la reserva
// se ejecutarían cada una por su cuenta, y un fallo a mitad dejaría stock descontado sin reserva (D-04).
@Configuration
public class TransactionConfig {

	@Bean
	MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
		return new MongoTransactionManager(databaseFactory);
	}

	// TransactionTemplate y no @Transactional: el reintento tiene que quedar afuera de la transacción, y con las
	// anotaciones ese orden depende de cómo Spring encadena los proxies. Así se ve en el código.
	@Bean
	TransactionTemplate transactionTemplate(MongoTransactionManager transactionManager) {
		return new TransactionTemplate(transactionManager);
	}

}
