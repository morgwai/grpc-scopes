/*
 * Copyright (c) Piotr Morgwai Kotarbinski
 */
package pl.morgwai.samples.grpc.scopes.domain;

import java.util.List;

import javax.annotation.Nonnull;



public interface RecordDao {

	List<Record> findAll() throws DaoException;
	void persist(@Nonnull Record record) throws DaoException;



	public static class DaoException extends Exception {

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
