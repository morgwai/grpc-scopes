// Copyright (c) Piotr Morgwai Kotarbinski, Licensed under the Apache License, Version 2.0
package pl.morgwai.samples.grpc.scopes.domain;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;



@Entity
public class RecordEntity implements Serializable {



	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	Long id;
	public static final String ID = "id";
	public Long getId() { return id; }

	String content;
	public static final String CONTENT = "content";
	public String getContent() { return content; }



	@Override
	public int hashCode() {
		return (id == null ? 0 : id.hashCode())
				+ 13 * (content == null ? 0 : content.hashCode());
	}



	@Override
	public boolean equals(Object other) {
		if (this == other) return true;
		if (other == null) return false;
		if (other.getClass() != RecordEntity.class) return false;
		RecordEntity otherRecord = (RecordEntity) other;
		return (id == null ? otherRecord.getId() == null : id.equals(otherRecord.getId()))
			&& (content == null ?
					otherRecord.getContent() == null : content.equals(otherRecord.getContent()));
	}



	// boilerplate only below

	public RecordEntity() {}

	public RecordEntity(String content) {
		this.content = content;
	}

	public RecordEntity(long id, String content) {
		this.id = id;
		this.content = content;
	}

	static {
		// unit-test/deploy time check if there are not typos in field names
		try {
			RecordEntity.class.getDeclaredField(ID);
			RecordEntity.class.getDeclaredField(CONTENT);
		} catch (NoSuchFieldException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	private static final long serialVersionUID = -4203604269278214911L;
}
