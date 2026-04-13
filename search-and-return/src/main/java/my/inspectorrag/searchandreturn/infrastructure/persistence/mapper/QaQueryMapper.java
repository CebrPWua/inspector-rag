package my.inspectorrag.searchandreturn.infrastructure.persistence.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface QaQueryMapper {

    @Select("""
            <script>
            select lc.id as chunk_id,
                   sd.law_name,
                   lc.article_no,
                   lc.content,
                   greatest(
                       ts_rank_cd(to_tsvector(cast(#{ftsLanguage} as regconfig), coalesce(lc.content, '')),
                                  websearch_to_tsquery(cast(#{ftsLanguage} as regconfig), #{keywordQuery})),
                       case when
                           <foreach collection="likeTerms" item="term" separator=" or " open="(" close=")">
                               lc.content ilike #{term}
                           </foreach>
                       then 0.60 else 0 end,
                       case when
                           <foreach collection="likeTerms" item="term" separator=" or " open="(" close=")">
                               sd.law_name ilike #{term}
                           </foreach>
                       then 0.80 else 0 end,
                       case when
                           <foreach collection="likeTerms" item="term" separator=" or " open="(" close=")">
                               coalesce(lc.article_no, '') ilike #{term}
                           </foreach>
                       then 0.70 else 0 end
                   ) as score,
                   lc.page_start,
                   lc.page_end,
                   sd.version_no
              from ingest.law_chunk lc
              join ingest.source_document sd on sd.id = lc.doc_id
             where sd.status = 'active'
               and lc.status = 'active'
               and (
                   to_tsvector(cast(#{ftsLanguage} as regconfig), coalesce(lc.content, ''))
                   @@ websearch_to_tsquery(cast(#{ftsLanguage} as regconfig), #{keywordQuery})
                   or
                   <foreach collection="likeTerms" item="term" separator=" or " open="(" close=")">
                       lc.content ilike #{term}
                   </foreach>
                   or
                   <foreach collection="likeTerms" item="term" separator=" or " open="(" close=")">
                       sd.law_name ilike #{term}
                   </foreach>
                   or
                   <foreach collection="likeTerms" item="term" separator=" or " open="(" close=")">
                       coalesce(lc.article_no, '') ilike #{term}
                   </foreach>
               )
             <if test="docTypes != null and docTypes.size > 0">
               and sd.doc_type in
               <foreach collection="docTypes" item="docType" open="(" separator="," close=")">
                 #{docType}
               </foreach>
             </if>
             <if test="publishOrg != null and publishOrg != ''">
               and sd.publish_org = #{publishOrg}
             </if>
             <if test="effectiveOn != null">
               and (sd.effective_date is null or sd.effective_date &lt;= #{effectiveOn})
               and (sd.expired_date is null or sd.expired_date &gt;= #{effectiveOn})
             </if>
             <if test="industryTags != null and industryTags.size > 0">
               and exists (
                   select 1
                     from ingest.chunk_tag ct
                    where ct.chunk_id = lc.id
                      and ct.tag_type = 'industry'
                      and ct.tag_value in
                      <foreach collection="industryTags" item="industryTag" open="(" separator="," close=")">
                        #{industryTag}
                      </foreach>
               )
             </if>
             order by score desc, lc.id asc
             limit #{topK}
            </script>
            """)
    List<RecallCandidateRow> keywordRecall(
            @Param("ftsLanguage") String ftsLanguage,
            @Param("keywordQuery") String keywordQuery,
            @Param("likeTerms") List<String> likeTerms,
            @Param("topK") int topK,
            @Param("docTypes") List<String> docTypes,
            @Param("publishOrg") String publishOrg,
            @Param("effectiveOn") LocalDate effectiveOn,
            @Param("industryTags") List<String> industryTags
    );

    @Select("""
            <script>
            select lc.id
              from ingest.law_chunk lc
              join ingest.source_document sd on sd.id = lc.doc_id
             where sd.status = 'active'
               and lc.status = 'active'
               and lc.id in
               <foreach collection="chunkIds" item="chunkId" open="(" separator="," close=")">
                 #{chunkId}
               </foreach>
             <if test="docTypes != null and docTypes.size > 0">
               and sd.doc_type in
               <foreach collection="docTypes" item="docType" open="(" separator="," close=")">
                 #{docType}
               </foreach>
             </if>
             <if test="publishOrg != null and publishOrg != ''">
               and sd.publish_org = #{publishOrg}
             </if>
             <if test="effectiveOn != null">
               and (sd.effective_date is null or sd.effective_date &lt;= #{effectiveOn})
               and (sd.expired_date is null or sd.expired_date &gt;= #{effectiveOn})
             </if>
             <if test="industryTags != null and industryTags.size > 0">
               and exists (
                   select 1
                     from ingest.chunk_tag ct
                    where ct.chunk_id = lc.id
                      and ct.tag_type = 'industry'
                      and ct.tag_value in
                      <foreach collection="industryTags" item="industryTag" open="(" separator="," close=")">
                        #{industryTag}
                      </foreach>
               )
             </if>
            </script>
            """)
    List<Long> filterChunkIdsByMetadata(
            @Param("chunkIds") List<Long> chunkIds,
            @Param("docTypes") List<String> docTypes,
            @Param("publishOrg") String publishOrg,
            @Param("effectiveOn") LocalDate effectiveOn,
            @Param("industryTags") List<String> industryTags
    );

    @Select("""
            <script>
            select distinct ct.chunk_id
              from ingest.chunk_tag ct
             where ct.tag_type = 'industry'
               and ct.chunk_id in
               <foreach collection="chunkIds" item="chunkId" open="(" separator="," close=")">
                 #{chunkId}
               </foreach>
               and ct.tag_value in
               <foreach collection="industryTags" item="industryTag" open="(" separator="," close=")">
                 #{industryTag}
               </foreach>
            </script>
            """)
    List<Long> findChunkIdsByIndustryTags(
            @Param("chunkIds") List<Long> chunkIds,
            @Param("industryTags") List<String> industryTags
    );

    @Select("""
            select id as qa_id, question, normalized_question, answer, answer_status, created_at
              from retrieval.qa_record
             where id = #{qaId}
            """)
    QaDetailRow findQaDetail(@Param("qaId") Long qaId);

    @Select("""
            select e.cite_no,
                   e.chunk_id,
                   e.law_name,
                   e.article_no,
                   e.quoted_text,
                   c.source_type,
                   c.final_score,
                   e.page_start,
                   e.page_end,
                   e.file_version
              from retrieval.qa_evidence e
              left join retrieval.qa_candidate c on c.qa_id = e.qa_id and c.chunk_id = e.chunk_id
             where e.qa_id = #{qaId}
             order by e.cite_no
            """)
    List<QaEvidenceRow> findQaEvidences(@Param("qaId") Long qaId);
}
