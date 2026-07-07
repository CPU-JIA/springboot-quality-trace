package com.xinghui.qualitytrace.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 业务单号流水 Mapper —— 基于唯一主键行的原子递增分配。 */
@Mapper
public interface SerialNumberMapper {

    @Insert("""
            INSERT INTO serial_number (serial_key, current_value)
            VALUES (#{serialKey}, LAST_INSERT_ID(1))
            ON DUPLICATE KEY UPDATE current_value = LAST_INSERT_ID(current_value + 1)
            """)
    int allocate(@Param("serialKey") String serialKey);

    @Select("SELECT LAST_INSERT_ID()")
    Integer lastAllocatedValue();
}
