/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.konan.gradle;

import com.intellij.execution.ExecutionTargetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.AtomicClearableLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.SmartList;
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment;
import com.jetbrains.cidr.lang.workspace.OCResolveConfiguration;
import com.jetbrains.cidr.lang.workspace.OCWorkspaceImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.konan.gradle.execution.GradleKonanBuildTarget;
import org.jetbrains.konan.gradle.execution.GradleKonanConfiguration;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.List;
import java.util.Map;


/**
 * @author Vladislav.Soroka
 */
public class GradleKonanWorkspace {

  public static final String LOADING_GRADLE_KONAN_PROJECT = "Loading Gradle Kotlin/Native Project...";
  private static final Logger LOG = Logger.getInstance(GradleKonanWorkspace.class);
  @NotNull private final AtomicClearableLazyValue<List<GradleKonanBuildTarget>> myTargets;
  private final Project myProject;
  @NotNull private final BackgroundTaskQueue myReloadsQueue;

  public GradleKonanWorkspace(@NotNull Project project) {
    myProject = project;
    myReloadsQueue = new BackgroundTaskQueue(project, LOADING_GRADLE_KONAN_PROJECT);
    myTargets = new AtomicClearableLazyValue<List<GradleKonanBuildTarget>>() {
      @NotNull
      @Override
      protected List<GradleKonanBuildTarget> compute() {
        return loadBuildTargets(project);
      }
    };
    // force reloading of the build targets when external data cache is ready
    ExternalProjectsManager.getInstance(project).runWhenInitialized(() -> update());
  }

  @NotNull
  public static GradleKonanWorkspace getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, GradleKonanWorkspace.class);
  }

  public List<GradleKonanBuildTarget> getModelTargets() {
    return myTargets.getValue();
  }

  @Nullable
  public OCResolveConfiguration getResolveConfigurationFor(@Nullable GradleKonanConfiguration configuration) {
    return configuration == null ? null : OCWorkspaceImpl.getInstanceImpl(myProject).getConfigurationById(configuration.getId());
  }

  public void update() {
    // Skip the update if no Gradle projects are linked with this IDE project.
    if (GradleSettings.getInstance(myProject).getLinkedProjectsSettings().isEmpty()) {
      return;
    }
    myReloadsQueue.run(new Task.Backgroundable(myProject, LOADING_GRADLE_KONAN_PROJECT) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        myTargets.drop();
        myTargets.getValue();
        updateOCWorkspace();
        ApplicationManager.getApplication().invokeLater(() -> ExecutionTargetManager.update(myProject), myProject.getDisposed());
      }
    });
  }

  public void updateOCWorkspace() {
    OCWorkspaceImpl.ModifiableModel workspace = OCWorkspaceImpl.getInstanceImpl(myProject).getModifiableModel();
    try {
      CidrToolEnvironment environment = new CidrToolEnvironment();

      NullableFunction<File, VirtualFile> fileMapper = OCWorkspaceImpl.createFileMapper();
      for (ExternalProjectInfo projectInfo : ProjectDataManager.getInstance().getExternalProjectsData(myProject, GradleConstants.SYSTEM_ID)) {
        Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> data =
                projectInfo.getExternalProjectStructure().getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS);
        for (DataNode<ModuleData> moduleNode : ExternalSystemApiUtil.findAll(projectInfo.getExternalProjectStructure(), ProjectKeys.MODULE)) {

        }
      }

      //KonanProjectDataService.forEachKonanProject(myProject, (cppProject, moduleData, rootProjectPath) -> {
      //  cppProject.getArtifacts().forEach(
      //    konanArtifact -> addConfiguration(
      //      konanArtifact,
      //      rootProjectPath,
      //      moduleData,
      //      workspace,
      //      environment,
      //      fileMapper)
      //  );
      //  return Unit.INSTANCE;
      //});

      TransactionGuard.getInstance().submitTransactionAndWait(() -> {
        ApplicationManager.getApplication().runWriteAction(() -> {
          if (myProject.isDisposed()) {
            workspace.dispose();
            return;
          }
          //workspace.commit(1);
        });
      });
    }
    finally {
      workspace.dispose();
    }
    //workspace.getMessages().forEach(each -> {
    //  LOG.warn(each.getType() + ": " + each.getText());
    //  // todo send messages to the build view (sync tab)
    //});
  }

  @NotNull
  private static List<GradleKonanBuildTarget> loadBuildTargets(@NotNull Project project) {
    List<GradleKonanBuildTarget> buildTargets = new SmartList<>();
    //KonanProjectDataService.forEachKonanProject(project, (konanModel, moduleData, rootProjectPath) -> {
    //  MultiMap<Trinity<String, String, String>, GradleKonanConfiguration> configurationsMap = MultiMap.createSmart();
    //  for (KonanModelArtifact konanArtifact: konanModel.getArtifacts()) {
    //    String compileTaskName = konanArtifact.getBuildTaskName();
    //    String id = getConfigurationId(moduleData.getId(), konanArtifact);
    //    // TODO: We should do something about debug/release for gradle
    //    GradleKonanConfiguration configuration =
    //      new GradleKonanConfiguration(id, konanArtifact.getName(), "Debug",
    //                                   konanArtifact.getFile(), konanArtifact.getType(),
    //                                   compileTaskName, rootProjectPath);
    //    Trinity<String, String, String> names = Trinity.create(moduleData.getId(), moduleData.getExternalName(), konanArtifact.getName());
    //    configurationsMap.putValue(names, configuration);
    //  }
    //
    //  configurationsMap.entrySet().forEach(entry -> {
    //    Trinity<String, String, String> names = entry.getKey();
    //    Collection<GradleKonanConfiguration> value = entry.getValue();
    //    List<GradleKonanConfiguration> configurations =
    //      value instanceof List ? (List<GradleKonanConfiguration>)value : ContainerUtil.newArrayList(value);
    //
    //    String moduleId = names.first;
    //    String moduleName = names.second;
    //    String targetName = names.third;
    //    String targetId = getBuildTargetId(moduleId, targetName);
    //    buildTargets.add(new GradleKonanBuildTarget(targetId, targetName, moduleName, configurations));
    //  });
    //  return Unit.INSTANCE;
    //});

    return buildTargets;
  }

  //@NotNull
  //private static String getConfigurationId(String moduleId, KonanModelArtifact konanArtifact) {
  //  return getBuildTargetId(moduleId, konanArtifact.getName()) + ":" + konanArtifact.getBuildTaskName();
  //}

  @NotNull
  private static String getBuildTargetId(String moduleId, String targetName) {
    return moduleId + ":" + targetName;
  }

  //private static void addConfiguration(KonanModelArtifact konanArtifact,
  //                                     String rootProjectPath,
  //                                     ModuleData moduleData,
  //                                     ModifiableModel workspace,
  //                                     CidrToolEnvironment environment,
  //                                     NullableFunction<File, VirtualFile> fileMapper) {
  //  String id = getConfigurationId(moduleData.getId(), konanArtifact);
  //  String displayName = OCResolveConfigurationImpl.getConfigurationDisplayName(konanArtifact.getName(), konanArtifact.getBuildTaskName(), false);
  //  String shortDisplayName = OCResolveConfigurationImpl.getConfigurationDisplayName(konanArtifact.getName(), konanArtifact.getBuildTaskName(), true);
  //
  //  //File buildWorkingDir = konanArtifact.getCompilerDetails().getWorkingDir();
  //  //if (buildWorkingDir == null) {
  //  //  workspace.getMessages().add(new Message(id, MessageType.ERROR, "Compiler working dir was not found for '" + displayName + "'"));
  //  //  return;
  //  //}
  //  //if (konanArtifact.getCompilerExecutable() == null) {
  //  //  workspace.getMessages().add(new Message(id, MessageType.ERROR, "Compiler was not found for '" + displayName + "'"));
  //  //  return;
  //  //}
  //  Map<OCLanguageKind, OCResolveConfigurationImpl.CompilerSettingsData> configLanguages = new THashMap<>();
  //
  //
  //  OCCompilerKind compilerKind = Arrays.stream(OCCompilerKind.values())
  //                                      //.filter(
  //                                      //  kind -> kind.toString().equalsIgnoreCase(konanArtifact.getCompilerDetails().getCompilerKind()))
  //                                      .findFirst()
  //                                      .orElse(OCCompilerKind.UNKNOWN);
  //  configLanguages.put(CLanguageKind.CPP, new OCResolveConfigurationImpl.CompilerSettingsData(compilerKind, null,
  //                                                                                             konanArtifact.getSrcDirs().get(0),
  //                                                                                             CidrCompilerSwitches.EMPTY));//new CidrSwitchBuilder().addAllRaw(compilerArgs).build()));
  //  Map<VirtualFile, Pair<OCLanguageKind, CidrCompilerSwitches>> configSourceFiles = new THashMap<>();
  //  for (File ioSource: konanArtifact.getSrcFiles()) {
  //    VirtualFile vfSource = fileMapper.fun(ioSource);
  //    if (vfSource == null) continue;
  //    configSourceFiles.put(vfSource, Pair.create(OCLanguageKind.CPP, null));
  //  }
  //
  //  if (!konanArtifact.getSrcDirs().isEmpty()) {
  //    workspace.addConfiguration(
  //      id, displayName, shortDisplayName, configLanguages, configSourceFiles, environment, fileMapper);
  //  }
  //}
}
