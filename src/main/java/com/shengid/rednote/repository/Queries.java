package com.shengid.rednote.repository;

import com.shengid.rednote.entity.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

/** Parameterized persistence queries. No authorization or transaction orchestration here. */
@Repository
public class Queries {
  @PersistenceContext private EntityManager em;

  private <T> TypedQuery<T> q(String jpql, Class<T> type, Object... args) {
    var q = em.createQuery(jpql, type);
    for (int i = 0; i < args.length; i += 2) q.setParameter((String) args[i], args[i + 1]);
    return q;
  }

  public Optional<AppUser> account(String name) {
    return q("from AppUser where usernameNormalized=:name", AppUser.class, "name", name)
        .getResultStream()
        .findFirst();
  }

  public List<AppUser> users(Collection<Long> ids) {
    return ids.isEmpty()
        ? List.of()
        : q("from AppUser where id in :ids", AppUser.class, "ids", ids).getResultList();
  }

  public List<FileAsset> files(Collection<Long> ids) {
    return ids.isEmpty()
        ? List.of()
        : q("from FileAsset where id in :ids", FileAsset.class, "ids", ids).getResultList();
  }

  public List<NoteImage> images(Collection<Long> ids) {
    return ids.isEmpty()
        ? List.of()
        : q(
                "from NoteImage where noteId in :ids order by noteId, sortOrder",
                NoteImage.class,
                "ids",
                ids)
            .getResultList();
  }

  private String pattern(String s) {
    return "%" + s.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }

  private static final String SEARCH =
      " deletedAt is null and id<=:upper and (title like :q escape '!' or body like :q escape '!')";

  public long upper() {
    return q("select coalesce(max(id),0) from NoteEntity", Long.class).getSingleResult();
  }

  public List<NoteEntity> feed(String query, long upper, Instant last, long lastId) {
    return q(
            "from NoteEntity where"
                + SEARCH
                + " and (publishedAt<:last or (publishedAt=:last and id<:id)) order by publishedAt"
                + " desc,id desc",
            NoteEntity.class,
            "upper",
            upper,
            "q",
            pattern(query),
            "last",
            last,
            "id",
            lastId)
        .setMaxResults(21)
        .getResultList();
  }

  public long feedCount(String query, long upper) {
    return q(
            "select count(*) from NoteEntity where" + SEARCH,
            Long.class,
            "upper",
            upper,
            "q",
            pattern(query))
        .getSingleResult();
  }

  public List<NoteEntity> profile(long id, int page) {
    return q(
            "from NoteEntity where authorId=:id and deletedAt is null order by publishedAt desc,id"
                + " desc",
            NoteEntity.class,
            "id",
            id)
        .setFirstResult(offset(page))
        .setMaxResults(20)
        .getResultList();
  }

  public static int offset(int page) {
    if (page < 0 || page > Integer.MAX_VALUE / 20)
      throw new com.shengid.rednote.web.UiException(400, "页码无效");
    return page * 20;
  }

  public long noteCount(long user) {
    return q(
            "select count(*) from NoteEntity where authorId=:id and deletedAt is null",
            Long.class,
            "id",
            user)
        .getSingleResult();
  }

  public Map<Long, Long> likes(Collection<Long> ids) {
    Map<Long, Long> out = new HashMap<>();
    if (!ids.isEmpty())
      for (var row :
          q(
                  "select id.noteId,count(*) from NoteLike where id.noteId in :ids group by"
                      + " id.noteId",
                  Object[].class,
                  "ids",
                  ids)
              .getResultList()) out.put((Long) row[0], (Long) row[1]);
    return out;
  }

  public Set<Long> liked(Collection<Long> ids, Long viewer) {
    return viewer == null || ids.isEmpty()
        ? Set.of()
        : new HashSet<>(
            q(
                    "select id.noteId from NoteLike where id.noteId in :ids and id.userId=:user",
                    Long.class,
                    "ids",
                    ids,
                    "user",
                    viewer)
                .getResultList());
  }

  public void like(long user, long note, boolean liked, Instant now) {
    var key = new NoteLike.Key(user, note);
    var old = em.find(NoteLike.class, key);
    if (liked && old == null) {
      var n = new NoteLike();
      n.id = key;
      n.createdAt = now;
      em.persist(n);
    }
    if (!liked && old != null) em.remove(old);
    em.flush();
  }

  public void removeLikes(long note) {
    em.createQuery("delete from NoteLike where id.noteId=:id")
        .setParameter("id", note)
        .executeUpdate();
  }

  public void removeImages(long note) {
    em.createQuery("delete from NoteImage where noteId=:id")
        .setParameter("id", note)
        .executeUpdate();
    em.flush();
  }

  private static final String VALID_COMMENT =
      " c.deletedAt is null and exists(select n.id from NoteEntity n where n.id=c.noteId and"
          + " n.deletedAt is null) and (c.rootCommentId is null or exists(select r.id from"
          + " CommentEntity r where r.id=c.rootCommentId and r.deletedAt is null))";

  public record CommentIdentity(long id, long noteId, Long rootId, long authorId) {}

  public Optional<CommentIdentity> commentIdentity(long id) {
    return q(
            "select c.id,c.noteId,c.rootCommentId,c.authorId from CommentEntity c where c.id=:id",
            Object[].class,
            "id",
            id)
        .getResultStream()
        .map(r -> new CommentIdentity((Long) r[0], (Long) r[1], (Long) r[2], (Long) r[3]))
        .findFirst();
  }

  public long comments(long note) {
    return q(
            "select count(c) from CommentEntity c where c.noteId=:id and" + VALID_COMMENT,
            Long.class,
            "id",
            note)
        .getSingleResult();
  }

  public List<CommentEntity> comments(long note, long root, int page) {
    String condition = root == 0 ? "c.rootCommentId is null" : "c.rootCommentId=:root";
    var query =
        q(
            "select c from CommentEntity c where c.noteId=:id and "
                + condition
                + " and"
                + VALID_COMMENT
                + " order by c.createdAt "
                + (root == 0 ? "desc" : "asc")
                + ",c.id "
                + (root == 0 ? "desc" : "asc"),
            CommentEntity.class,
            "id",
            note);
    if (root != 0) query.setParameter("root", root);
    return query.setFirstResult(offset(page)).setMaxResults(21).getResultList();
  }

  public List<CommentEntity> lockComments(long note, Long root) {
    var query =
        q(
            "from CommentEntity where noteId=:note"
                + (root == null ? "" : " and (id=:root or rootCommentId=:root)")
                + " order by id",
            CommentEntity.class,
            "note",
            note);
    if (root != null) query.setParameter("root", root);
    return query.setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
  }

  public boolean referenced(long file) {
    return q("select count(*) from AppUser where avatarFileId=:id", Long.class, "id", file)
                .getSingleResult()
            > 0
        || q("select count(*) from NoteImage where fileId=:id", Long.class, "id", file)
                .getSingleResult()
            > 0;
  }

  public boolean publiclyReferenced(long file) {
    return q("select count(*) from AppUser where avatarFileId=:id", Long.class, "id", file)
                .getSingleResult()
            > 0
        || q(
                    "select count(i) from NoteImage i,NoteEntity n where i.noteId=n.id and"
                        + " n.deletedAt is null and i.fileId=:id",
                    Long.class,
                    "id",
                    file)
                .getSingleResult()
            > 0;
  }

  private static final String USER_FILTER =
      "username like :username escape '!' and nickname like :name escape '!' and (:status='' or"
          + " status=:status)";

  public List<AppUser> adminUsers(String username, String name, String status, int page) {
    return q(
            "from AppUser where " + USER_FILTER + " order by createdAt desc,id desc",
            AppUser.class,
            "username",
            pattern(username),
            "name",
            pattern(name),
            "status",
            status)
        .setFirstResult(offset(page))
        .setMaxResults(20)
        .getResultList();
  }

  public long adminUserCount(String username, String name, String status) {
    return q(
            "select count(*) from AppUser where " + USER_FILTER,
            Long.class,
            "username",
            pattern(username),
            "name",
            pattern(name),
            "status",
            status)
        .getSingleResult();
  }

  public long usersTotal() {
    return q("select count(*) from AppUser where role='USER'", Long.class).getSingleResult();
  }

  public long notesTotal() {
    return q("select count(*) from NoteEntity where deletedAt is null", Long.class)
        .getSingleResult();
  }

  public long likesTotal() {
    return q(
            "select count(l) from NoteLike l,NoteEntity n where l.id.noteId=n.id and n.deletedAt is"
                + " null",
            Long.class)
        .getSingleResult();
  }

  public long commentsTotal() {
    return q("select count(c) from CommentEntity c where" + VALID_COMMENT, Long.class)
        .getSingleResult();
  }

  public long newUsers(Instant start, Instant end) {
    return q(
            "select count(*) from AppUser where role='USER' and createdAt>=:start and"
                + " createdAt<:end",
            Long.class,
            "start",
            start,
            "end",
            end)
        .getSingleResult();
  }

  public long published(Instant start, Instant end) {
    return q(
            "select count(*) from NoteEntity where publishedAt>=:start and publishedAt<:end",
            Long.class,
            "start",
            start,
            "end",
            end)
        .getSingleResult();
  }

  public List<Long> cleanupCandidates(Instant ready, Instant staging, long after) {
    return q(
            "select id from FileAsset where id>:after and (state='DELETING' or (state='READY' and"
                + " unreferencedSince<=:ready) or (state='STAGING' and createdAt<=:staging)) order"
                + " by id",
            Long.class,
            "after",
            after,
            "ready",
            ready,
            "staging",
            staging)
        .setMaxResults(100)
        .getResultList();
  }

  public boolean storageKeyExists(String key) {
    return q(
                "select count(*) from FileAsset where storageKey=:key and state<>'DELETED'",
                Long.class,
                "key",
                key)
            .getSingleResult()
        > 0;
  }

  public long overdueCleanup(Instant ready, Instant deleting, Instant staging) {
    return q(
            "select count(*) from FileAsset where (state='READY' and unreferencedSince<=:ready) or"
                + " (state='DELETING' and updatedAt<=:deleting) or (state='STAGING' and"
                + " createdAt<=:staging)",
            Long.class,
            "ready",
            ready,
            "deleting",
            deleting,
            "staging",
            staging)
        .getSingleResult();
  }
}
