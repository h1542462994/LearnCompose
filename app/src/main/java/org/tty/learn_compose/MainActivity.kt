package org.tty.learn_compose

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.WorkerThread
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.tty.learn_compose.ui.theme.LearnComposeTheme

class MainActivity : ComponentActivity() {
    // create the wordViewModel by Factory
    private val wordViewModel: WordViewModel by viewModels {
        WordViewModelFactory((application as WordsApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LearnComposeTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    WordScreen(wordViewModel = wordViewModel)
                }
            }
        }
    }
}

@Composable
fun WordScreen(wordViewModel: WordViewModel) {
    val words by wordViewModel.allWords.observeAsState(listOf())
    WordContent(words, onInsertWord = { wordViewModel.insert(it) })
}

@Composable
fun WordContent(words: List<Word>, onInsertWord: (Word) -> Unit = {}) {
    var text by remember { mutableStateOf("") }
    Column(modifier = Modifier.padding(16.dp)) {
        TextField(text, onValueChange = { text = it })
        Button(onClick = { onInsertWord(Word(text)) }) {
            Text("添加单词")
        }
        repeat(words.size) { index ->
           Text(words[index].word)
        }
    }
}

@Preview
@Composable
fun WordContentPreview() {
    val wordList = listOf<Word>(Word("第一个单词"), Word("第二个单词"))
    WordContent(wordList)
}

@Entity(tableName = "word_table")
data class Word(
    @PrimaryKey
    @ColumnInfo(name = "word")
    val word: String
)

@Dao
interface WordDao {
    @Query("SELECT * FROM word_table ORDER BY word ASC")
    fun getAlphabetizedWords(): Flow<List<Word>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(word: Word)

    @Query("DELETE FROM word_table")
    suspend fun deleteAll()
}

@Database(entities = [Word::class], version = 1, exportSchema = false)
abstract class WordRoomDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao

    private class WordDatabaseCallBack(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch {
                    populateDatabase(database.wordDao())
                }
            }
        }

        suspend fun populateDatabase(wordDao: WordDao) {
            //wordDao.deleteAll()

            var word = Word("Hello")
            wordDao.insert(word)
            word = Word("World!")
            wordDao.insert(word)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: WordRoomDatabase? = null

        fun getDataBase(context: Context, scope: CoroutineScope): WordRoomDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WordRoomDatabase::class.java,
                    "word_database"
                )
                    .addCallback(WordDatabaseCallBack(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class WordRepository(private val wordDao: WordDao) {
    val allWords: Flow<List<Word>> = wordDao.getAlphabetizedWords()

    @WorkerThread
    suspend fun insert(word: Word) {
        wordDao.insert(word)
    }
}

class WordViewModel(private val repository: WordRepository): ViewModel() {
    val allWords: LiveData<List<Word>> = repository.allWords.asLiveData()

    fun insert(word: Word) = viewModelScope.launch {
        repository.insert(word)
    }
}

class WordViewModelFactory(private val repository: WordRepository): ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WordViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class WordsApplication: Application() {
    val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { WordRoomDatabase.getDataBase(this, applicationScope) }
    val repository by lazy { WordRepository(database.wordDao()) }
}

//region deprecated codes

@Composable
fun Greeting(name: String) {
    Text(text = "Hello $name!")
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    LearnComposeTheme {
        Greeting("Android")
    }
}

@Composable
fun HelloScreen(helloViewModel: HelloViewModel = viewModel()) {
    val name: String by helloViewModel.name.observeAsState("")
    HelloContent(name = name, onNameChange = { helloViewModel.onNameChange(it) })
}

@Composable
fun HelloContent(name: String, onNameChange: (String) -> Unit = {}) {
    Column(modifier = Modifier.padding(16.dp)) {
        if (name.isNotEmpty()) {
            Text(
                text = "Hello, $name",
                modifier = Modifier.padding(bottom = 8.dp),
                style = MaterialTheme.typography.h5
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = { onNameChange(it) },
            label = { Text("Name") }
        )
    }
}

@Preview
@Composable
fun HelloContentPreview() {
    HelloContent("Text")
}

class HelloViewModel() : ViewModel() {
    private val _name = MutableLiveData("")
    val name: LiveData<String> = _name


    fun onNameChange(newName: String) {
        _name.value = newName
    }
}

//endregion