// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.domain;

import java.util.List;



public interface RecordDao {

	List<RecordEntity> findAll() throws DaoException;
	void persist( RecordEntity record) throws DaoException;

	class DaoException extends Exception {
		public DaoException(Throwable cause) { super(cause); }
	}
}
