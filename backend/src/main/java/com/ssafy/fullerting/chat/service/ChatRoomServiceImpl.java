package com.ssafy.fullerting.chat.service;

import com.ssafy.fullerting.chat.model.dto.request.CreateChatRoomRequest;
import com.ssafy.fullerting.chat.model.dto.response.CreateChatRoomResponse;
import com.ssafy.fullerting.chat.model.dto.response.GetAllChatRoomResponse;
import com.ssafy.fullerting.chat.model.entity.Chat;
import com.ssafy.fullerting.chat.model.entity.ChatRoom;
import com.ssafy.fullerting.chat.repository.ChatRepository;
import com.ssafy.fullerting.chat.repository.ChatRoomRepository;
import com.ssafy.fullerting.exArticle.exception.ExArticleErrorCode;
import com.ssafy.fullerting.exArticle.exception.ExArticleException;
import com.ssafy.fullerting.exArticle.model.entity.ExArticle;
import com.ssafy.fullerting.exArticle.repository.ExArticleRepository;
import com.ssafy.fullerting.user.exception.UserErrorCode;
import com.ssafy.fullerting.user.exception.UserException;
import com.ssafy.fullerting.user.model.dto.response.UserResponse;
import com.ssafy.fullerting.user.model.entity.CustomUser;
import com.ssafy.fullerting.user.repository.UserRepository;
import com.ssafy.fullerting.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ssafy.fullerting.exArticle.exception.ExArticleErrorCode.NOT_EXISTS;
import static com.ssafy.fullerting.user.exception.UserErrorCode.NOT_EXISTS_USER;

@RequiredArgsConstructor
@Service
@Slf4j
public class ChatRoomServiceImpl implements ChatRoomService{
    private final UserService userService;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRepository chatRepository;
    private final ExArticleRepository exArticleRepository;
    private final UserRepository userRepository;

    @Override
    public CreateChatRoomResponse createChatRoom(CreateChatRoomRequest createChatRoomRequest) {
        UserResponse userResponse = userService.getUserInfo();

        //이미 존재하는 채팅방인지 조회
        Optional<ChatRoom> chatRoomOptional = chatRoomRepository.findByExArticleIdAndBuyerId(createChatRoomRequest.getExArticleId(), userResponse.getId());
        //존재하지 않을 경우 채팅방 생성
        if(chatRoomOptional.isEmpty()){
            ChatRoom chatRoom = chatRoomRepository.save(ChatRoom.builder()
                    .exArticleId(createChatRoomRequest.getExArticleId())
                    .buyerId(userResponse.getId())
                    .build());
            return CreateChatRoomResponse.toResponse(chatRoom);
        }
        //존재하는 경우 해당 채팅방 응답
        else{
            return CreateChatRoomResponse.toResponse(chatRoomOptional.get());
        }
    }

    @Override
    public List<GetAllChatRoomResponse> getAllChatRoom() {
        //현재 유저 정보 받아오기
        UserResponse userResponse = userService.getUserInfo();

        //현재 유저가 구매자이거나 판매자인 경우 모든 채팅방 조회
        List<ChatRoom> chatRoomList = chatRoomRepository.findByBuyerIdOrExArticleUserId(userResponse.getId());

        return chatRoomList.stream()
                // 채팅방 리스트를 최신 채팅 메시지가 먼저 오도록 정렬
                .sorted((chatRoom1, chatRoom2) -> {
                    Chat lastChat1 = chatRepository.findLatestChatByChatRoomId(chatRoom1.getId());
                    Chat lastChat2 = chatRepository.findLatestChatByChatRoomId(chatRoom2.getId());

                    // 최신 채팅 메시지가 없는 경우에는 정렬하지 않음
                    if (lastChat1 == null && lastChat2 == null) {
                        return 0;
                    } else if (lastChat1 == null) {
                        return 1; // lastChat2가 존재하면 lastChat1 보다 나중으로 간주
                    } else if (lastChat2 == null) {
                        return -1; // lastChat1이 존재하면 lastChat2 보다 나중으로 간주
                    } else {
                        return lastChat2.getSendAt().compareTo(lastChat1.getSendAt());
                    }
                })
                .map(chatRoom -> {
                    //상대방 ID 조회
                    ExArticle exArticle = exArticleRepository.findById(chatRoom.getExArticleId()).orElseThrow(() -> new ExArticleException(NOT_EXISTS));
                    CustomUser otherUser = null;
                    //현재 유저가 판매자와 동일할 경우
                    if (exArticle.getUser().getId().equals(userResponse.getId())) {
                        //상대방 정보에 구매자 저장
                        otherUser = userRepository.findById(chatRoom.getBuyerId()).orElseThrow(() -> new UserException(NOT_EXISTS_USER));
                    }
                    //현재 유저가 구매자와 동일할 경우
                    else {
                        //상대방 정보에 판매자 저장
                        otherUser = exArticle.getUser();
                    }

                    //채팅방 최근 메시지 조회
                    Chat lastChat = chatRepository.findLatestChatByChatRoomId(chatRoom.getId());

                    return GetAllChatRoomResponse.builder()
                            .chatRoomId(chatRoom.getId())
                            .chatRoomOtherThumb(otherUser.getThumbnail())
                            .chatOtherNick(otherUser.getNickname())
                            .chatRoomLastMessage(lastChat != null ? lastChat.getMessage() : null)
                            .chatRoomLastMessageSendAt(lastChat != null ? lastChat.getSendAt() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
