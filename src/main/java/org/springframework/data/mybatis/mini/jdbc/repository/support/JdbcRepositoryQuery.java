/*
 * Copyright 2018-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mybatis.mini.jdbc.repository.support;

import com.vonchange.jdbc.abstractjdbc.core.JdbcRepository;
import com.vonchange.jdbc.abstractjdbc.handler.AbstractPageWork;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mybatis.mini.jdbc.repository.config.BindParameterWrapper;
import org.springframework.data.mybatis.mini.jdbc.repository.config.ConfigInfo;
import org.springframework.data.mybatis.mini.relational.core.mapping.RelationalMappingContext;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * A query to be executed based on a repository method, it's annotated SQL query and the arguments provided to the
 * method.
 *
 * @author Jens Schauder
 * @author Kazuki Shimizu
 * @author Oliver Gierke
 * @author Maciej Walkowiak
 */
@Slf4j
class JdbcRepositoryQuery implements RepositoryQuery {

	private static final String PARAMETER_NEEDS_TO_BE_NAMED = "For queries with named parameters you need to provide names for method parameters. Use @Param for query method parameters, or when on Java 8+ use the javac flag -parameters.";

	private final JdbcQueryMethod queryMethod;
	private final JdbcRepository operations;
	private final ConfigInfo configInfo;

	/**
	 * Creates a new {@link JdbcRepositoryQuery} for the given {@link JdbcQueryMethod}, {@link RelationalMappingContext} and
	 * {@link RowMapper}.
	 *
	 * @param queryMethod must not be {@literal null}.
	 * @param operations must not be {@literal null}.
	 */
	JdbcRepositoryQuery(JdbcQueryMethod queryMethod, JdbcRepository operations, ConfigInfo configInfo) {

		Assert.notNull(queryMethod, "Query method must not be null!");
		Assert.notNull(operations, "NamedParameterJdbcOperations must not be null!");
		Assert.notNull(configInfo, "configLocation must not be null!");
		this.queryMethod = queryMethod;
		this.operations = operations;
		this.configInfo=configInfo;
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.query.RepositoryQuery#execute(java.lang.Object[])
	 */
	@Override
	public Object execute(Object[] objects) {
	    return executeDo(objects);
	}
	private Object executeDo(Object[] objects){
		BindParameterWrapper parameters = bindParameter(objects);
		String sqlId= configInfo.getLocation()+"."+configInfo.getMethod();
		if (configInfo.getMethod().startsWith("update")||configInfo.getMethod().startsWith("delete")) {
			int updatedCount = operations.update(sqlId,parameters.getParameter());
			Class<?> returnedObjectType = queryMethod.getReturnedObjectType();
			return (returnedObjectType == boolean.class || returnedObjectType == Boolean.class) ? updatedCount != 0
					: updatedCount;
		}
		if (configInfo.getMethod().startsWith("insert")||configInfo.getMethod().startsWith("save")) {
			return operations.insert(sqlId,parameters.getParameter());
		}

		if (queryMethod.isCollectionQuery() || queryMethod.isStreamQuery()) {
			/*if(ClassUtils.isAssignable(Map.class,queryMethod.getReturnedObjectType())){
				return operations.queryList(sqlId,parameters.getParameter());
			}*/
			return operations.queryList(queryMethod.getReturnedObjectType(), sqlId,parameters.getParameter());
		}
		if(queryMethod.isPageQuery()){
			if(null==parameters.getAbstractPageWork()){
				return operations.queryPage(queryMethod.getReturnedObjectType(), sqlId,parameters.getPageable(),parameters.getParameter());
			}
			return operations.queryBigData(queryMethod.getReturnedObjectType(),sqlId,parameters.getAbstractPageWork(),parameters.getParameter());
		}
		if(com.vonchange.jdbc.abstractjdbc.util.clazz.ClassUtils.isBaseType(queryMethod.getReturnedObjectType())){
			return operations.queryOneColumn(queryMethod.getReturnedObjectType(),sqlId,parameters.getParameter());
		}
		try {
			return operations.queryOne(queryMethod.getReturnedObjectType(),sqlId,parameters.getParameter());
		} catch (EmptyResultDataAccessException e) {
			return null;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see org.springframework.data.repository.query.RepositoryQuery#getQueryMethod()
	 */
	@Override
	public JdbcQueryMethod getQueryMethod() {
		return queryMethod;
	}


	private BindParameterWrapper bindParameter(Object[] objects) {

		Map<String,Object> map = new HashMap<>();
		BindParameterWrapper bindParameterWrapper = new BindParameterWrapper();
		if(objects.length>0){
			Class<?> type = queryMethod.getParameters().getParameter(0).getType();
			if(ClassUtils.isAssignable(Pageable.class, type)){
				Pageable pageable =(Pageable) objects[0];
				bindParameterWrapper.setPageable(pageable);
			}
			if(ClassUtils.isAssignable(AbstractPageWork.class, type)){
				bindParameterWrapper.setAbstractPageWork((AbstractPageWork) objects[0]);
			}
		}
		queryMethod.getParameters().getBindableParameters().forEach(p -> {
			//Param annotation =
			//return Optional.ofNullable(annotation == null ? parameter.getParameterName() : annotation.value());
				String parameterName = p.getName().orElseThrow(() -> new IllegalStateException(PARAMETER_NEEDS_TO_BE_NAMED));
				map.put(parameterName, objects[p.getIndex()]);
		});
		bindParameterWrapper.setParameter(map);
		return bindParameterWrapper;
	}







}
