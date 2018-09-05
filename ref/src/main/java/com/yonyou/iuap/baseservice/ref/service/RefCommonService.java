package com.yonyou.iuap.baseservice.ref.service;


import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yonyou.iuap.baseservice.entity.Model;
import com.yonyou.iuap.baseservice.entity.RefParamConfig;
import com.yonyou.iuap.baseservice.persistence.support.QueryFeatureExtension;
import com.yonyou.iuap.mvc.type.SearchParams;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.yonyou.iuap.baseservice.entity.RefParamVO;
import com.yonyou.iuap.baseservice.entity.annotation.Reference;
import com.yonyou.iuap.baseservice.persistence.utils.RefXMLParse;
import com.yonyou.iuap.baseservice.ref.dao.mapper.RefCommonMapper;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ReflectUtil;


/**
 * 通用参照服务,基于ref.XML解析,生成动态sql检索,此配置文件应部署在业务项目中,例如iuap-pap-quickStart/src/main/resources/ref.xml
 * @author leon
 * @Date 2018-07-11
 */
@Service
public  class RefCommonService<T extends Model>  implements QueryFeatureExtension<T>{
    private static Logger logger= LoggerFactory.getLogger(RefCommonService.class);


    @Autowired
    RefCommonMapper mapper;

    public List<Map<String, Object>> getFilterRef(String tablename, String idfield,
                                                  List<String> extColumns, List<String> ids) {

        List<Map<String, Object>> result = mapper.findRefListByIds(tablename,idfield,extColumns,ids);

        return result;
    }

    public Page<Map<String, Object>> getTreeRefData(PageRequest pageRequest,String refType, RefParamConfig refParamConfigTable, String content,String fid, Set<String> ids) {
        StringBuilder keyword=new StringBuilder();

        Map<String, String> conditions =new HashMap<>();

        Map<String, String> conditionQuoter = new HashMap<>();

        setCondition(refParamConfigTable,keyword,conditions,conditionQuoter,content);

        List<String> idList=null;
        if(ids!=null&&ids.size()>0){
            idList=new ArrayList<>(ids);
        }
        Page<Map<String,Object>> result = mapper.treerefselectAllByPage(pageRequest,refParamConfigTable.getTableName(),refParamConfigTable.getId(),
                refParamConfigTable.getRefcode(),refParamConfigTable.getRefname(), refParamConfigTable.getExtension(),keyword.toString(),conditions,conditionQuoter,refParamConfigTable.getCondition(),refParamConfigTable.getFid(),fid,
                idList).getPage();

        return result;

    }

    public Page<Map<String, Object>> getCheckboxData(PageRequest pageRequest,String refType, RefParamConfig refParamConfigTable, String content, Set<String> ids) {
        StringBuilder keyword=new StringBuilder();

        Map<String, String> conditions =new HashMap<>();

        Map<String, String> conditionQuoter = new HashMap<>();

        setCondition(refParamConfigTable,keyword,conditions,conditionQuoter,content);

        List<String> idList=null;
        if(ids!=null&&ids.size()>0){
            idList=new ArrayList<>(ids);
        }

    	Page<Map<String,Object>> result = mapper.selectRefCheck(pageRequest,refParamConfigTable.getTableName(),refParamConfigTable.getId(),
                refParamConfigTable.getRefcode(),refParamConfigTable.getRefname(), refParamConfigTable.getExtension(),keyword.toString(),conditions,conditionQuoter,refParamConfigTable.getCondition(),
                idList).getPage();

    	return result;
    }

    public Page<Map<String, Object>> selectRefTree(PageRequest pageRequest,String refType, RefParamConfig refParamConfigTableTree, String content, Set<String> ids) {
        StringBuilder keyword=new StringBuilder();

        Map<String, String> conditions =new HashMap<>();

        Map<String, String> conditionQuoter = new HashMap<>();


        setCondition(refParamConfigTableTree,keyword,conditions,conditionQuoter,content);

        List<String> idList=null;
        if(ids!=null&&ids.size()>0){
            idList=new ArrayList<>(ids);
        }
        Page<Map<String,Object>> result = mapper.selectRefTree(pageRequest,refParamConfigTableTree.getTableName(),refParamConfigTableTree.getId(),
                refParamConfigTableTree.getRefcode(),refParamConfigTableTree.getRefname(),refParamConfigTableTree.getPid(), refParamConfigTableTree.getExtension(),keyword.toString(),conditions,conditionQuoter,refParamConfigTableTree.getCondition(),
                idList).getPage();
        return result;
    }
    private void setCondition( RefParamConfig refParamConfigTable,StringBuilder keyword,Map<String, String> conditions,Map<String, String> conditionQuoter,String content){

        try {
            conditions.putAll(JSON.parseObject(content,Map.class));
            Map<String,String> filters=refParamConfigTable.getFilters();
            List<String> removeKeys=new ArrayList<>();
            for(String key : conditions.keySet()){
                String filter=filters.get(key);
                if(StringUtils.isEmpty(filter)){
                    removeKeys.add(key);
                    logger.error(key+"值对应的filter类型不存在，请验证");
                    continue;
                }
                if("like".equalsIgnoreCase(filter.trim())){
                    conditions.put(key,"%"+conditions.get(key)+"%");
                }
                conditionQuoter.put(key,filter);
            }
            for(String removeKey:removeKeys){
                conditions.remove(removeKey);
            }
        }catch (Exception e){
            if(StringUtils.isNotEmpty(content)){
                keyword.append("%").append(content).append("%");
            }
        }
    }

    /**
     * 参照数据加载,根据 @See  com.yonyou.iuap.baseservice.entity.annotation.Reference
     * 中定义的参照参数,将参照数据中检索出来的值写到对应的entity属性中,以便前端展示
     * @param list 未装填参照的原始list
     * @return 重新装填后的结果
     */
    public List fillListWithRef(List list){
        if (list!=null&&!list.isEmpty()) {
            Map<Field,Set<String>>idCache = new HashMap<>(); //缓存list中的所有entity属性参照内的id
            Map<Field, Reference> refCache = new HashMap<>();//缓存entity中的所有@Reference定义
            Map<Field, List<Map<String, Object>>> refDataCache = new HashMap<>();//缓存参照数据,用于最后的反写
            /**
             * @Step 1
             * 解析参照配置,获取参照字段id集合,用于后续参照查询
             */
            boolean isFirst = true;
            for (Object entity : list) {
                Field[] fields = ReflectUtil.getFields(entity.getClass());
                for (Field field : fields) {
                    Reference ref = field.getAnnotation(Reference.class);
                    if (null != ref) {
                        if (isFirst) { //  提高缓存装载效率,仅加载一次
                            refCache.put(field, ref); //将所有参照和field的关系缓存起来后续使用
                            idCache.put(field,new HashSet<String>());
                        }
                        Object refIds = ReflectUtil.getFieldValue(entity, field);
                        if (null!=refIds){
                            String [] fieldIds = refIds.toString().split(",");//兼容参照多选
                            idCache.get(field).addAll(Arrays.asList(fieldIds));
                         }
                    }
                }
                isFirst = false;
            }
            /**
             * @Step 2解析参照配置,一次按需(idCache)加载参照数据
             */

            for (Field field : refCache.keySet()) {
                RefParamVO refParamVO = RefXMLParse.getInstance().getReParamConfig(refCache.get(field).code());
                RefParamConfig refParamConfig=refParamVO.getRefParamConfigTable()==null?refParamVO.getRefParamConfigTableTree():refParamVO.getRefParamConfigTable();
                if (refParamVO==null||refParamConfig==null){
                    logger.warn("参照XML配置错误:"+refCache.get(field).code());
                    continue;
                }
                List<String> setList = new ArrayList<>(idCache.get(field));
                if (setList==null || setList.size()==0){
                    continue;
                }
                List<Map<String, Object>> refContents =
                        mapper.findRefListByIds(refParamConfig.getTableName(),
                                refParamConfig.getId(), refParamConfig.getExtension(), setList);
                if ( null!= refContents && refContents.size()>0)
                    refDataCache.put(field, refContents);//将所有参照数据集和field的关系缓存起来后续使用
            }
            /**
             * @Step 3 逐条遍历业务结果集,向entity参照指定属性写入参照值
             */
            if (!refDataCache.isEmpty()) {
                for (Object entity : list) { //遍历结果集
                    for(Field refField: refCache.keySet() ){//遍历缓存的entity的全部参照字段
                        if (refDataCache.get(refField)== null){
                            continue;//没有参照数据缓存,就不用后面的反写了,直接下一个参照字段
                        }
                        Reference refAnnotation = refCache.get(refField);
                        if (  ReflectUtil.getFieldValue(entity,refField) == null ){
                            continue; // 参照field id值为空,则跳过本field数据解析
                        }
                        String refFieldValue = ReflectUtil.getFieldValue(entity,refField).toString();//取参照字段值
                        String[] mutiRefIds = refFieldValue.split(",");     //参照字段值转数组
                        String[] mutiRefValues = new String[mutiRefIds.length];  //定义结果载体
                        int loopSize =Math.min( refAnnotation.srcProperties().length ,refAnnotation.desProperties().length  );//参照配置多字段参照时需结构匹配
                        for (int i = 0; i < loopSize; i++) {                //遍历参照中配置的多个srcPro和desPro 进行值替换
                            String srcCol = refAnnotation.srcProperties()[i];  //参照表value字段
                            String desField= refAnnotation.desProperties()[i]; //entity对应参照value的字段
                            List<Map<String, Object>> refDatas =refDataCache.get(refField);//取出参照缓存数据集
                            for (Map<String,Object> refData: refDatas){
                                for (int j = 0; j <mutiRefIds.length ; j++) {//多值参照时,循环匹配拿到结果进行反写
                                    if (refData.get("ID")!=null && refData.get("ID").toString().equals(mutiRefIds[j])){ //数据库适配时 mysql也要将此字段as ID
                                        for(String columnKey:refData.keySet()){//解决大小写适配问题
                                            if (columnKey.equalsIgnoreCase(srcCol))
                                                mutiRefValues[j] = String.valueOf( refData.get(columnKey) );
                                        }
                                    }
                                }
                            }
                            String fieldValue =ArrayUtil.join(mutiRefValues,",");
                            ReflectUtil.setFieldValue(entity, desField,fieldValue); //执行反写
                        }

                    }
                }
            }
        }

        return list;
    }

    @Override
    public SearchParams prepareQueryParam(SearchParams searchParams,Class modelClass) {
        return searchParams;
    }

    /**
     * 通过特性集成，实现了在查询时与其他模块的任意组合
     * @param list 未装填参照的原始list
     * @return 重新装填后的结果
     */
    @Override
    public List<T> afterListQuery(List<T> list) {
        return this.fillListWithRef(list);
    }



    public List<Map<String, Object>> getByIds(String tablename, String idfield,String codefield, String namefield,
                                              List<String> extColumns, List<String> ids) {

        List<Map<String, Object>> result = mapper.getByIds(tablename,idfield, codefield,  namefield,extColumns,ids);

        return result;
    }
    public List<Map<String, Object>> likeSearch(String tablename, String idfield,String codefield, String namefield,
                                                List<String> extColumns, List<String> ids,String keyword) {
        return mapper.likeSearch(tablename,idfield, codefield,  namefield,extColumns,ids,keyword);
    }
}
