package com.yonyou.iuap.baseservice.persistence.mybatis.ext.adapter.mssql;

import com.yonyou.iuap.baseservice.persistence.mybatis.ext.adapter.AutoMapperFactory;
import com.yonyou.iuap.baseservice.persistence.mybatis.ext.adapter.SqlTemplate;
import com.yonyou.iuap.baseservice.persistence.mybatis.ext.support.Dialect;
import org.apache.ibatis.mapping.SqlCommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 说明：新增mssql数据库支持
 * @author leon
 * 2018年10月10日
 */
public class MssqlMapperFactory implements AutoMapperFactory{
	
	private Logger log = LoggerFactory.getLogger(MssqlMapperFactory.class);

	private Integer isInit = 0;
	private ReadWriteLock LOCK = new ReentrantReadWriteLock();
	
	private Map<SqlCommandType, SqlTemplate> sqlTemplateMap = new HashMap<SqlCommandType, SqlTemplate>();


	private void initSqlTemplate() {
		SqlTemplate deleteTemplate = new MssqlDeleteTemplate();
		SqlTemplate updateTemplate = new MssqlUpdateTemplate();
		SqlTemplate insertTemplate = new MssqlInsertTemplate();
		SqlTemplate selectTemplate = new MssqlSelectTemplate();
		sqlTemplateMap.put(deleteTemplate.getSQLType(), deleteTemplate);
		sqlTemplateMap.put(updateTemplate.getSQLType(), updateTemplate);
		sqlTemplateMap.put(insertTemplate.getSQLType(), insertTemplate);
		sqlTemplateMap.put(selectTemplate.getSQLType(), selectTemplate);
		log.info("初始化SqlTemple成功,已加载insertTemplate,updateTemplate,deleteTemplate,selectTemplate!");
	}
	
	@Override
	public Dialect getDialect() {
		return Dialect.mssql;
	}

	@Override
	public SqlTemplate getSqlTempalte(SqlCommandType sqlType) {
		if(isInit == 0) {
			LOCK.writeLock().lock();
			try {
				if(isInit == 0) {
					this.initSqlTemplate();
					isInit = 1;
				}
			}finally {
				LOCK.writeLock().unlock();
			}
		}
		return sqlTemplateMap.get(sqlType);
	}

	@Override
	public String parseSQL4Insert(Method method, Class<?> entityClazz) {
		return this.getSqlTempalte(SqlCommandType.INSERT).parseSQL(method, entityClazz);
	}

	@Override
	public String parseSQL4Update(Method method, Class<?> entityClazz) {
		return this.getSqlTempalte(SqlCommandType.UPDATE).parseSQL(method, entityClazz);
	}

    @Override
    public String parseSQL4InsertSelective(Method method, Class<?> entityClazz) {
        return new MssqlInsertSelectiveTemplate().parseSQL(method,entityClazz);
    }

    @Override
    public String parseSQL4BatchInsert(Method method, Class<?> entityClazz) {
        return new MssqlBatchInsertTemplate().parseSQL(method,entityClazz);
    }

    @Override
    public String parseSQL4UpdateSelective(Method method, Class<?> entityClazz) {
        return new MssqlUpdateSelectiveTemplate().parseSQL(method,entityClazz);
    }

    @Override
	public String parseSQL4Delete(Method method, Class<?> entityClazz) {
		return this.getSqlTempalte(SqlCommandType.DELETE).parseSQL(method, entityClazz);
	}

	@Override
	public String parseSQL4Select(Method method, Class<?> entityClazz) {
		return this.getSqlTempalte(SqlCommandType.SELECT).parseSQL(method, entityClazz);
	}

}