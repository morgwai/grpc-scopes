// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.domain;

import java.util.List;

import javax.annotation.Nonnull;



public interface RecordDao {

	List<RecordEntity> findAll() throws DaoException;
	void persist(@Nonnull RecordEntity record) throws DaoException;



	class DaoException extends Exception {

		public DaoException() {}
		public DaoException(String message, Throwable cause, boolean enableSuppression,
				boolean writableStackTrace) {
			super(message, cause, enableSuppression, writableStackTrace);
		}
		public DaoException(String message, Throwable cause) {
			super(message, cause);
		}
		public DaoException(String message) {
			super(message);
		}
		public DaoException(Throwable cause) {
			super(cause);
		}
		private static final long serialVersionUID = 9063193299208511869L;
	}
}
