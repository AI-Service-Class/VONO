package my.vono.web.model.meeting;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import my.vono.web.entity.Folder;
import my.vono.web.entity.Meeting;


public interface MeetingDAO extends JpaRepository<Meeting,Long>{
	
	@Query("select m from Meeting m where m.is_trash=true and m.member.id=:id and m.folder.id is null")
	List<Meeting>findTrashMeetingByMemberId(@Param("id")Long id);
	
	
	//검색 select *from vono.meeting where name  like "%회의%" and member_id=1 and is_trash = 0;
	@Query("select m from Meeting m where m.name like %:name% and m.is_trash=false and m.member.id=:id")
	List<Meeting>findMeetingWithNameAndMemberId(@Param("id")Long id,@Param("name")String name);
	
	//폴더 별 회의록 리스트 select * from vono.meeting where folder_id=2 and member_id=1;
	//@Query("select m from Meeting m where m.folder_id=folder and m.member.id=:id")
	//List<Meeting>findMeetingByFolderId(@Param("id")Long id,@Param("folder")Folder folder);

}
