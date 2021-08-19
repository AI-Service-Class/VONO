/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package my.vono.web.gspeech;

// [START speech_transcribe_infinite_streaming]

import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.SpeakerDiarizationConfig;
import com.google.cloud.speech.v1p1beta1.RecognitionConfig;
import com.google.cloud.speech.v1p1beta1.SpeechClient;
import com.google.cloud.speech.v1p1beta1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1p1beta1.StreamingRecognitionConfig;
import com.google.cloud.speech.v1p1beta1.StreamingRecognitionResult;
import com.google.cloud.speech.v1p1beta1.StreamingRecognizeRequest;
import com.google.cloud.speech.v1p1beta1.StreamingRecognizeResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;

import my.vono.web.controller.MeetingController;

import java.text.DecimalFormat;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.TargetDataLine;

import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.ui.Model;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.internal.build.AllowSysOut;


public class InfiniteStreamRecognize {

  private static final int STREAMING_LIMIT = 290000; // ~5 minutes

  public static final String RED = "\033[0;31m";
  public static final String GREEN = "\033[0;32m";
  public static final String YELLOW = "\033[0;33m";

  // Creating shared object
  private static volatile BlockingQueue<byte[]> sharedQueue = new LinkedBlockingQueue();
  private static TargetDataLine targetDataLine;
  private static int BYTES_PER_BUFFER = 6400; // buffer size in bytes

  private static int restartCounter = 0;
  private static ArrayList<ByteString> audioInput = new ArrayList<ByteString>();
  private static ArrayList<ByteString> lastAudioInput = new ArrayList<ByteString>();
  private static int resultEndTimeInMS = 0;
  private static int isFinalEndTime = 0;
  private static int finalRequestEndTime = 0;
  private static boolean newStream = true;
  private static double bridgingOffset = 0;
  private static boolean lastTranscriptWasFinal = false;
  private static StreamController referenceToStreamController;
  private static ByteString tempByteString;

  //계속 써져야 하는 자원 밖으로(여기) 꺼내보기
	//.xlsx 확장자 지원
	static XSSFWorkbook xssfWb = new XSSFWorkbook(); // .xlsx
	static XSSFSheet xssfSheet = xssfWb.createSheet("VONO_1"); // 워크시트 생성
	static XSSFRow xssfRow = null; // .xlsx
	static XSSFCell xssfCell = null;// .xlsx
	static int rowNo = 1; // 행 갯수
	static String localFile="C:\\" + "VONO_테스트_엑셀" + ".xlsx";
	static File file = new File(localFile);
    
  public static void StreamStart(Model model, String... args ) {
    InfiniteStreamRecognizeOptions options = InfiniteStreamRecognizeOptions.fromFlags(args);
    if (options == null) {
      // Could not parse.
      System.out.println("Failed to parse options.");
      System.exit(1);
    }
    
    try {
//		infiniteStreamingRecognize(options.langCode);
//    	infiniteStreamingRecognize("en-US");
    	infiniteStreamingRecognize("ko-KR", model);
      
    } catch (Exception e) {
      System.out.println("Exception caught: " + e);
    }  
  }

  public static String convertMillisToDate(double milliSeconds) {
    long millis = (long) milliSeconds;
    DecimalFormat format = new DecimalFormat();
    format.setMinimumIntegerDigits(2);
    return String.format(
        "%s:%s /",
        format.format(TimeUnit.MILLISECONDS.toMinutes(millis)),
        format.format(
            TimeUnit.MILLISECONDS.toSeconds(millis)
                - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))));
  }

  /** Performs infinite streaming speech recognition */
  public static void infiniteStreamingRecognize(String languageCode, Model model) throws Exception {

    // Microphone Input buffering
    class MicBuffer implements Runnable {

      @Override
      public void run() {
        //System.out.println(YELLOW);
        System.out.println("Start speaking...Press Ctrl-C to stop");
        
        //한번만 실행되는 구문
        xssfSheet.setColumnWidth(2, (xssfSheet.getColumnWidth(2))+(short)10240); // 2번째 컬럼 넓이 조절
		xssfRow = xssfSheet.createRow(0);	//0번째 row는 헤더
		xssfCell = xssfRow.createCell((short) 0);
		xssfCell.setCellValue("시간");
		xssfCell = xssfRow.createCell((short) 1);
		xssfCell.setCellValue("화자구분");
		xssfCell = xssfRow.createCell((short) 2);
		xssfCell.setCellValue("내용");
		
        
        targetDataLine.start();
        byte[] data = new byte[BYTES_PER_BUFFER];
        while (targetDataLine.isOpen()) {
          try {
            int numBytesRead = targetDataLine.read(data, 0, data.length);
            if ((numBytesRead <= 0) && (targetDataLine.isOpen())) {
              continue;
            }
            sharedQueue.put(data.clone());
          } catch (InterruptedException e) {
            System.out.println("Microphone input buffering interrupted : " + e.getMessage());
          }
        }
      }
    }

    // Creating microphone input buffer thread
    MicBuffer micrunnable = new MicBuffer();
    Thread micThread = new Thread(micrunnable);
    ResponseObserver<StreamingRecognizeResponse> responseObserver = null;
    try (SpeechClient client = SpeechClient.create()) {
      ClientStream<StreamingRecognizeRequest> clientStream;
      responseObserver =
          new ResponseObserver<StreamingRecognizeResponse>() {

            ArrayList<StreamingRecognizeResponse> responses = new ArrayList<>();

            public void onStart(StreamController controller) {
              referenceToStreamController = controller;
            }

            public void onResponse(StreamingRecognizeResponse response) {
              responses.add(response);
              StreamingRecognitionResult result = response.getResultsList().get(0);
              Duration resultEndTime = result.getResultEndTime();
              resultEndTimeInMS =
                  (int)
                      ((resultEndTime.getSeconds() * 1000) + (resultEndTime.getNanos() / 1000000));
              double correctedTime =
                  resultEndTimeInMS - bridgingOffset + (STREAMING_LIMIT * restartCounter);

              SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
              
              
              //여기에서 바로 res를 웹에다가 띄우는 방법 설계
              if (result.getIsFinal()) {
                String res= 
//                		alternative.toString();
          			  convertMillisToDate(correctedTime)+
          			  "화자"+ alternative.getWords(0).getSpeakerTag()+
          			  " :" + alternative.getTranscript()+
          			  "\n";
                
        		model.addAttribute("time", convertMillisToDate(correctedTime).split(" ")[0]) 
        			.addAttribute("speaker", alternative.getWords(0).getSpeakerTag())
        			.addAttribute("transcript", alternative.getTranscript());
        		System.out.println(model);
//        			{time=00:05, speaker=1, transcript=난 더 더 더 더 크게 되어} //예시
//        			{time=00:14, speaker=1, transcript= 널 가득 안고 싶고 그래요}
        			
        		// 엑셀 임포트 구문
    			try {
    				
    				
    				System.out.println("rowNo : "+rowNo);
    				xssfRow = xssfSheet.createRow(rowNo++);
    				xssfCell = xssfRow.createCell((short) 0);
    				xssfCell.setCellValue(convertMillisToDate(correctedTime).split(" ")[0]); //시간
    				xssfCell = xssfRow.createCell((short) 1);
    				xssfCell.setCellValue("화자 "+alternative.getWords(0).getSpeakerTag()); //화자
    				xssfCell = xssfRow.createCell((short) 2);
    				xssfCell.setCellValue(alternative.getTranscript()); //내용
    				localFile = "C:\\" + "VONO_테스트_엑셀" + ".xlsx";
    				
    				File file = new File(localFile);
    				FileOutputStream fos = null;
    				fos = new FileOutputStream(file);
    				xssfWb.write(fos);
    				
    				//계속 엑셀에 써야하므로 끄면 안됨
//        				if (xssfWb != null)	xssfWb.close();
//        				if (fos != null) fos.close();
    			
    			}catch(Exception e){
    	        	System.out.println("row 생성에 실패");
    			}finally{
    				
    		    }
                
        		
        		
                isFinalEndTime = resultEndTimeInMS;
                lastTranscriptWasFinal = true;
              } else {
                lastTranscriptWasFinal = false;
              }
            }

            public void onComplete() {}

            public void onError(Throwable t) {}
          };
      clientStream = client.streamingRecognizeCallable().splitCall(responseObserver);
      
      
      
      //화자 구분위해 추가
//      SpeakerDiarizationConfig speakerDiarizationConfig =
//    	        SpeakerDiarizationConfig.newBuilder()
//    	            .setEnableSpeakerDiarization(true)
//    	            .setMinSpeakerCount(2)
//    	            .setMaxSpeakerCount(5)
//    	            .build();
      
      //베타버전으로 해야 적용됨
      com.google.cloud.speech.v1p1beta1.SpeakerDiarizationConfig speakerDiarizationConfig2 =
    		  com.google.cloud.speech.v1p1beta1.SpeakerDiarizationConfig.newBuilder()
    		  .setEnableSpeakerDiarization(true)
    		  .setMinSpeakerCount(2)
    		  .setMaxSpeakerCount(5)
    		  .build();
      
      
      //공식문서에서도 enableSpeakerDiarization과 diarizationSpeakerCount이 deprecated 되어있음
      //대신 diarizationConfig 사용할 것을 권장
      //https://cloud.google.com/speech-to-text/docs/reference/rest/v1p1beta1/RecognitionConfig?hl=ko
      RecognitionConfig recognitionConfig =
              RecognitionConfig.newBuilder()
                  .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                  .setLanguageCode(languageCode)
                  .setSampleRateHertz(16000)
                  .setDiarizationConfig(speakerDiarizationConfig2)
                  .build();


      StreamingRecognitionConfig streamingRecognitionConfig =
          StreamingRecognitionConfig.newBuilder()
              .setConfig(recognitionConfig)
              .setInterimResults(true)
              .build();

      StreamingRecognizeRequest request =
          StreamingRecognizeRequest.newBuilder()
              .setStreamingConfig(streamingRecognitionConfig)
              .build(); // The first request in a streaming call has to be a config

      clientStream.send(request);

      try {
        // SampleRate:16000Hz, SampleSizeInBits: 16, Number of channels: 1, Signed: true,
        // bigEndian: false
        AudioFormat audioFormat = new AudioFormat(16000, 16, 1, true, false);
        DataLine.Info targetInfo =
            new Info(
                TargetDataLine.class,
                audioFormat); // Set the system information to read from the microphone audio
        // stream

        if (!AudioSystem.isLineSupported(targetInfo)) {
          System.out.println("Microphone not supported");
          System.exit(0);
        }
        // Target data line captures the audio stream the microphone produces.
        targetDataLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
        targetDataLine.open(audioFormat);
        micThread.start();

        long startTime = System.currentTimeMillis();

        while (true) {

          long estimatedTime = System.currentTimeMillis() - startTime;

          if (estimatedTime >= STREAMING_LIMIT) {

            clientStream.closeSend();
            referenceToStreamController.cancel(); // remove Observer

            if (resultEndTimeInMS > 0) {
              finalRequestEndTime = isFinalEndTime;
            }
            resultEndTimeInMS = 0;

            lastAudioInput = null;
            lastAudioInput = audioInput;
            audioInput = new ArrayList<ByteString>();

            restartCounter++;

            if (!lastTranscriptWasFinal) {
              System.out.print('\n');
            }

            newStream = true;

            clientStream = client.streamingRecognizeCallable().splitCall(responseObserver);

            request =
                StreamingRecognizeRequest.newBuilder()
                    .setStreamingConfig(streamingRecognitionConfig)
                    .build();

            System.out.println(YELLOW);
            System.out.printf("%d: RESTARTING REQUEST\n", restartCounter * STREAMING_LIMIT);

            startTime = System.currentTimeMillis();

          } else {

            if ((newStream) && (lastAudioInput.size() > 0)) {
              // if this is the first audio from a new request
              // calculate amount of unfinalized audio from last request
              // resend the audio to the speech client before incoming audio
              double chunkTime = STREAMING_LIMIT / lastAudioInput.size();
              // ms length of each chunk in previous request audio arrayList
              if (chunkTime != 0) {
                if (bridgingOffset < 0) {
                  // bridging Offset accounts for time of resent audio
                  // calculated from last request
                  bridgingOffset = 0;
                }
                if (bridgingOffset > finalRequestEndTime) {
                  bridgingOffset = finalRequestEndTime;
                }
                int chunksFromMs =
                    (int) Math.floor((finalRequestEndTime - bridgingOffset) / chunkTime);
                // chunks from MS is number of chunks to resend
                bridgingOffset =
                    (int) Math.floor((lastAudioInput.size() - chunksFromMs) * chunkTime);
                // set bridging offset for next request
                for (int i = chunksFromMs; i < lastAudioInput.size(); i++) {
                  request =
                      StreamingRecognizeRequest.newBuilder()
                          .setAudioContent(lastAudioInput.get(i))
                          .build();
                  clientStream.send(request);
                }
              }
              newStream = false;
            }

            tempByteString = ByteString.copyFrom(sharedQueue.take());

            request =
                StreamingRecognizeRequest.newBuilder().setAudioContent(tempByteString).build();

            audioInput.add(tempByteString);
          }

          clientStream.send(request);
        }
      } catch (Exception e) {
        System.out.println(e);
      }
    }
  }
}
// [END speech_transcribe_infinite_streaming]