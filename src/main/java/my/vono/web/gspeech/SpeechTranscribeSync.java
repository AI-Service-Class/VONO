/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// DO NOT EDIT! This is a generated sample ("Request",  "speech_transcribe_sync")
// sample-metadata:
//   title: Transcribe Audio File (Local File)
//   description: Transcribe a short audio file using synchronous speech recognition
//   usage: gradle run -PmainClass=com.google.cloud.examples.speech.v1.SpeechTranscribeSync [--args='[--local_file_path "resources/brooklyn_bridge.raw"]']

package my.vono.web.gspeech;

import com.google.cloud.speech.v1.RecognitionAudio;
import com.google.cloud.speech.v1.RecognitionConfig;
import com.google.cloud.speech.v1.RecognizeRequest;
import com.google.cloud.speech.v1.RecognizeResponse;
import com.google.cloud.speech.v1.SpeechClient;
import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
import com.google.cloud.speech.v1.SpeechRecognitionResult;
import com.google.protobuf.ByteString;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

public class SpeechTranscribeSync {
  // [START speech_transcribe_sync]
  /*
   * Please include the following imports to run this sample.
   *
   * import com.google.cloud.speech.v1.RecognitionAudio;
   * import com.google.cloud.speech.v1.RecognitionConfig;
   * import com.google.cloud.speech.v1.RecognizeRequest;
   * import com.google.cloud.speech.v1.RecognizeResponse;
   * import com.google.cloud.speech.v1.SpeechClient;
   * import com.google.cloud.speech.v1.SpeechRecognitionAlternative;
   * import com.google.cloud.speech.v1.SpeechRecognitionResult;
   * import com.google.protobuf.ByteString;
   * import java.nio.file.Files;
   * import java.nio.file.Path;
   * import java.nio.file.Paths;
   */

  public static void sampleRecognize() {
    // TODO(developer): Replace these variables before running the sample.
    String localFilePath = "resources/brooklyn_bridge.raw";
    sampleRecognize(localFilePath);
  }

  /**
   * Transcribe a short audio file using synchronous speech recognition
   *
   * @param localFilePath Path to local audio file, e.g. /path/audio.wav
   */
  public static void sampleRecognize(String localFilePath) {
    try (SpeechClient speechClient = SpeechClient.create()) {

      // The language of the supplied audio
      String languageCode = "en-US";

      // Sample rate in Hertz of the audio data sent
      int sampleRateHertz = 16000;

      // Encoding of audio data sent. This sample sets this explicitly.
      // This field is optional for FLAC and WAV audio formats.
      RecognitionConfig.AudioEncoding encoding = RecognitionConfig.AudioEncoding.LINEAR16;
      RecognitionConfig config =
          RecognitionConfig.newBuilder()
              .setLanguageCode(languageCode)
              .setSampleRateHertz(sampleRateHertz)
              .setEncoding(encoding)
              .build();
      Path path = Paths.get(localFilePath);
      byte[] data = Files.readAllBytes(path);
      ByteString content = ByteString.copyFrom(data);
      RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(content).build();
      RecognizeRequest request =
          RecognizeRequest.newBuilder().setConfig(config).setAudio(audio).build();
      RecognizeResponse response = speechClient.recognize(request);
      for (SpeechRecognitionResult result : response.getResultsList()) {
        // First alternative is the most probable result
        SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
        System.out.printf("Transcript: %s\n", alternative.getTranscript());
      }
    } catch (Exception exception) {
      System.err.println("Failed to create the client due to: " + exception);
    }
  }
  // [END speech_transcribe_sync]

  public static void main(String[] args) throws Exception {
    Options options = new Options();
    options.addOption(
        Option.builder("").required(false).hasArg(true).longOpt("local_file_path").build());

    CommandLine cl = (new DefaultParser()).parse(options, args);
    String localFilePath = cl.getOptionValue("local_file_path", "resources/brooklyn_bridge.raw");

    sampleRecognize(localFilePath);
  }
}