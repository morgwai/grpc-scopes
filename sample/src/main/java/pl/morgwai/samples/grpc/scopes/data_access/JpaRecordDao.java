/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.grpc.scopes.data_access;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import pl.morgwai.samples.grpc.scopes.domain.Record;
import pl.morgwai.samples.grpc.scopes.domain.RecordDao;



public class JpaRecordDao implements RecordDao {



	Provider<EntityManager> entityManagerProvider;



	@Inject
	public JpaRecordDao(
			EntityManagerFactory persistenceUnit,
			Provider<EntityManager> entityManagerProvider
	) {
		this.entityManagerProvider = entityManagerProvider;

		// create named queries
		EntityManager initialEntityManager = persistenceUnit.createEntityManager();
		persistenceUnit.addNamedQuery(
				FIND_ALL_QUERY_NAME, initialEntityManager.createQuery(FIND_ALL_QUERY));
		initialEntityManager.close();
	}



	static final String FIND_ALL_QUERY_NAME = JpaRecordDao.class.getName() + ".findAll";
	static final String FIND_ALL_QUERY = "select r from "
			+ Record.class.getSimpleName() + " r";

	@Override
	public List<Record> findAll() throws DaoException {
		try {
			return entityManagerProvider.get()
					.createNamedQuery(FIND_ALL_QUERY_NAME, Record.class).getResultList();
		} catch (Exception e) {
			throw new DaoException(e);
		}
	}



	@Override
	public void persist(Record record) throws DaoException {
		try {
			entityManagerProvider.get().persist(record);
		} catch (Exception e) {
			throw new DaoException(e);
		}
	}
}
