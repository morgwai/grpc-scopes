// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.base.grpc.scopes.tests.server.data_access;

import java.util.List;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;

import pl.morgwai.base.grpc.scopes.tests.server.domain.RecordDao;
import pl.morgwai.base.grpc.scopes.tests.server.domain.RecordEntity;



public class JpaRecordDao implements RecordDao {



	final Provider<EntityManager> entityManagerProvider;



	@Inject
	public JpaRecordDao(
			EntityManagerFactory persistenceUnit,
			Provider<EntityManager> entityManagerProvider
	) {
		this.entityManagerProvider = entityManagerProvider;

		// create named queries
		final var initialEntityManager = persistenceUnit.createEntityManager();
		persistenceUnit.addNamedQuery(
				FIND_ALL_QUERY_NAME, initialEntityManager.createQuery(FIND_ALL_QUERY));
		initialEntityManager.close();
	}



	static final String FIND_ALL_QUERY_NAME = JpaRecordDao.class.getName() + ".findAll";
	static final String FIND_ALL_QUERY = "select r from "
			+ RecordEntity.class.getSimpleName() + " r";

	@Override
	public List<RecordEntity> findAll() throws DaoException {
		try {
			return entityManagerProvider.get()
					.createNamedQuery(FIND_ALL_QUERY_NAME, RecordEntity.class).getResultList();
		} catch (Exception e) {
			throw new DaoException(e);
		}
	}



	@Override
	public void persist(@Nonnull RecordEntity record) throws DaoException {
		try {
			entityManagerProvider.get().persist(record);
		} catch (Exception e) {
			throw new DaoException(e);
		}
	}
}
