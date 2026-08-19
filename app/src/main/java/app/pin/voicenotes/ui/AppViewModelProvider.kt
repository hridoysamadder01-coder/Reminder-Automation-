package app.pin.voicenotes.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pin.voicenotes.PinApp
import app.pin.voicenotes.ui.capture.CaptureViewModel
import app.pin.voicenotes.ui.detail.DetailViewModel
import app.pin.voicenotes.ui.home.HomeViewModel
import app.pin.voicenotes.ui.notes.NotesViewModel
import app.pin.voicenotes.ui.reminders.RemindersViewModel

object AppViewModelProvider {

    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            val app = pinApp()
            CaptureViewModel(
                app = app,
                repository = app.container.repository,
                parser = app.container.intentParser,
                speech = app.container.speech,
                alarmScheduler = app.container.scheduler,
            )
        }
        initializer { HomeViewModel(pinApp().container.repository) }
        initializer { NotesViewModel(pinApp().container.repository) }
        initializer { RemindersViewModel(pinApp().container.repository) }
        initializer {
            val app = pinApp()
            DetailViewModel(
                savedStateHandle = createSavedStateHandle(),
                repository = app.container.repository,
                alarmScheduler = app.container.scheduler,
                appScope = app.container.appScope,
            )
        }
    }

    private fun CreationExtras.pinApp(): PinApp =
        (this[AndroidViewModelFactory.APPLICATION_KEY] as Application) as PinApp
}
